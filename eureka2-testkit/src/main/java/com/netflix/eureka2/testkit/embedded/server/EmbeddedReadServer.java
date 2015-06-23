package com.netflix.eureka2.testkit.embedded.server;

import java.util.Arrays;
import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.EurekaReadServerModule;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.interest.FullFetchBatchingRegistry;
import com.netflix.eureka2.server.interest.FullFetchInterestClient;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.metric.client.EurekaClientMetricFactory.clientMetrics;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServer extends EmbeddedEurekaServer<EurekaServerConfig, ReadServerReport> {
    private final String serverId;
    private final ServerResolver registrationResolver;
    private final ServerResolver discoveryResolver;
    private final NetworkRouter networkRouter;

    public EmbeddedReadServer(String serverId,
                              EurekaServerConfig config,
                              ServerResolver registrationResolver,
                              ServerResolver discoveryResolver,
                              NetworkRouter networkRouter,
                              boolean withExt,
                              boolean withDashboard) {
        super(ServerType.Read, config, withExt, withDashboard);
        this.serverId = serverId;
        this.registrationResolver = registrationResolver;
        this.discoveryResolver = discoveryResolver;
        this.networkRouter = networkRouter;
    }

    @Override
    protected Module getModule() {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(registrationResolver)
                .build();

        // TODO We need to better encapsulate EurekaInterestClient construction
        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new FullFetchBatchingRegistry<>();
        BatchAwareIndexRegistry<InstanceInfo> indexRegistry = new BatchAwareIndexRegistry<>(
                new IndexRegistryImpl<InstanceInfo>(), remoteBatchingRegistry);

        BasicEurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().build();
        SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(indexRegistry, registryMetrics());
        ClientChannelFactory<InterestChannel> channelFactory = new InterestChannelFactory(
                serverId,
                transportConfig,
                discoveryResolver,
                registry,
                remoteBatchingRegistry,
                clientMetrics()
        );

        EurekaInterestClient interestClient = new FullFetchInterestClient(registry, channelFactory);

        Module embeddedReadServerModule = Modules.override(new EurekaReadServerModule(config, registrationClient, interestClient))
                .with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        if (networkRouter != null) {
                            bind(NetworkRouter.class).toInstance(networkRouter);
                            bind(TcpInterestServer.class).to(EmbeddedTcpInterestServer.class).in(Scopes.SINGLETON);
                        }
                    }
                });

        return Modules.combine(Arrays.asList(
                super.getModule(),
                embeddedReadServerModule
        ));
    }

    @Override
    protected void loadInstanceProperties(Properties props) {
        super.loadInstanceProperties(props);
        props.setProperty("eureka.client.discovery-endpoint.port", Integer.toString(config.getDiscoveryPort()));
    }

    public int getDiscoveryPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(TcpInterestServer.class).serverPort();
    }

    @Override
    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost").withPort(getDiscoveryPort());
    }

    @Override
    public ReadServerReport serverReport() {
        return new ReadServerReport(getDiscoveryPort(), getHttpServerPort(), getWebAdminPort());
    }

    public static class ReadServerReport extends AbstractServerReport {
        private final int discoveryPort;

        public ReadServerReport(int discoveryPort, int httpServerPort, int adminPort) {
            super(httpServerPort, adminPort);
            this.discoveryPort = discoveryPort;
        }

        public int getDiscoveryPort() {
            return discoveryPort;
        }
    }
}