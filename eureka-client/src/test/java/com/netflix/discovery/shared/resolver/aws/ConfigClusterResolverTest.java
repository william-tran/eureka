package com.netflix.discovery.shared.resolver.aws;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ConfigClusterResolverTest {

    private final EurekaClientConfig clientConfig = mock(EurekaClientConfig.class);
    private final List<String> endpointsC = Arrays.asList(
            "http://1.1.1.1:8000/eureka/v2/",
            "http://1.1.1.2:8000/eureka/v2/",
            "http://1.1.1.3:8000/eureka/v2/"
    );
    private final List<String> endpointsD = Arrays.asList(
            "http://1.1.2.1:8000/eureka/v2/",
            "http://1.1.2.2:8000/eureka/v2/"
    );
    private final List<String> endpointsE = Arrays.asList(
            "http://1.1.3.1:8000/eureka/v2/"
    );
    private final List<String> endpointsF = Arrays.asList(
            "https://1.1.4.1:8443/eureka/v2/"
    );
    private ConfigClusterResolver resolver;

    @Before
    public void setUp() {
        when(clientConfig.shouldUseDnsForFetchingServiceUrls()).thenReturn(false);
        when(clientConfig.getRegion()).thenReturn("us-east-1");
        when(clientConfig.getAvailabilityZones("us-east-1")).thenReturn(new String[]{"us-east-1c", "us-east-1d", "us-east-1e"});
        when(clientConfig.getEurekaServerServiceUrls("us-east-1c")).thenReturn(endpointsC);
        when(clientConfig.getEurekaServerServiceUrls("us-east-1d")).thenReturn(endpointsD);
        when(clientConfig.getEurekaServerServiceUrls("us-east-1e")).thenReturn(endpointsE);

        InstanceInfo instanceInfo = new InstanceInfo.Builder(InstanceInfoGenerator.takeOne())
                .setDataCenterInfo(new MyDataCenterInfo(DataCenterInfo.Name.MyOwn))
                .build();

        resolver = new ConfigClusterResolver(clientConfig, instanceInfo);
    }

    @Test
    public void testReadFromConfig() {
        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.size(), equalTo(6));
    }

    @Test
    public void testReadHttpsUrlFromConfig() {
        when(clientConfig.getAvailabilityZones("us-east-1")).thenReturn(new String[]{"us-east-1f"});
        when(clientConfig.getEurekaServerServiceUrls("us-east-1f")).thenReturn(endpointsF);     
        
        endpoints = resolver.getClusterEndpoints();
        
        assertThat(endpoints.size(), equalTo(1));
        assertThat(endpoints.get(0).isSecure(), equalTo(true));
    }
}
