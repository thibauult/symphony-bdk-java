package com.symphony.bdk.core.client.loadbalancing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.symphony.bdk.core.client.ApiClientFactory;
import com.symphony.bdk.core.config.model.BdkConfig;
import com.symphony.bdk.core.config.model.BdkLoadBalancingConfig;
import com.symphony.bdk.core.config.model.BdkLoadBalancingMode;
import com.symphony.bdk.core.config.model.BdkRetryConfig;
import com.symphony.bdk.core.config.model.BdkServerConfig;
import com.symphony.bdk.core.test.MockApiClient;
import com.symphony.bdk.gen.api.SignalsApi;
import com.symphony.bdk.gen.api.model.AgentInfo;
import com.symphony.bdk.http.api.ApiClient;
import com.symphony.bdk.http.api.ApiException;
import com.symphony.bdk.http.api.ApiRuntimeException;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class LoadBalancingStrategyTest {

  @Test
  void testNewInstanceRandomLB() {
    LoadBalancingStrategy loadBalancingStrategy = getLoadBalancingStrategy(BdkLoadBalancingMode.RANDOM);
    assertEquals(RandomLoadBalancingStrategy.class, loadBalancingStrategy.getClass());
  }

  @Test
  void testNewInstanceRoundRobinLB() {
    LoadBalancingStrategy loadBalancingStrategy = getLoadBalancingStrategy(BdkLoadBalancingMode.ROUND_ROBIN);
    assertEquals(RoundRobinLoadBalancingStrategy.class, loadBalancingStrategy.getClass());
  }

  @Test
  void testNewInstanceExternalLB() {
    LoadBalancingStrategy loadBalancingStrategy =
        getLoadBalancingStrategy(BdkLoadBalancingMode.EXTERNAL, Collections.singletonList(""));
    assertEquals(ExternalLoadBalancingStrategy.class, loadBalancingStrategy.getClass());
  }

  @Test
  void testRoundRobinLbStrategy() {
    LoadBalancingStrategy loadBalancingStrategy = getLoadBalancingStrategy(BdkLoadBalancingMode.ROUND_ROBIN,
        Arrays.asList("agent1", "agent2", "agent3"));

    List<String> basePaths = Stream.generate(loadBalancingStrategy::getNewBasePath).limit(4)
        .collect(Collectors.toList());

    assertEquals(Arrays.asList("https://agent1:443", "https://agent2:443", "https://agent3:443", "https://agent1:443"),
        basePaths);
  }

  @Test
  void testRandomLbStrategy() {
    LoadBalancingStrategy loadBalancingStrategy = getLoadBalancingStrategy(BdkLoadBalancingMode.RANDOM,
        Arrays.asList("agent1", "agent2", "agent3"));

    Map<String, Long> basePaths = Stream.generate(loadBalancingStrategy::getNewBasePath).limit(1000)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    assertTrue(basePaths.get("https://agent1:443") > 1);
    assertTrue(basePaths.get("https://agent2:443") > 1);
    assertTrue(basePaths.get("https://agent3:443") > 1);
  }

  @Test
  void testExternalLbWithApiClientMock() {
    MockApiClient mockApiClient = new MockApiClient();
    mockApiClient.onGet("/agent/v1/info", "{ \"serverFqdn\": \"https://agent1:443/context\" }");

    ApiClientFactory apiClientFactory = mock(ApiClientFactory.class);
    when(apiClientFactory.getRegularAgentClient("https://agent-lb:443"))
        .thenReturn(mockApiClient.getApiClient("/agent"));

    BdkConfig config = getBdkConfig(BdkLoadBalancingMode.EXTERNAL, Collections.singletonList("agent-lb"));
    LoadBalancingStrategy instance = LoadBalancingStrategyFactory.getInstance(config, apiClientFactory);

    assertEquals("https://agent1:443/context", instance.getNewBasePath());
  }

  @Test
  void testExternalLbStrategy() throws ApiException {
    SignalsApi signalsApi = mock(SignalsApi.class);
    when(signalsApi.v1InfoGet())
        .thenReturn(new AgentInfo().serverFqdn("https://agent1:443"))
        .thenReturn(new AgentInfo().serverFqdn("https://agent2:443"))
        .thenReturn(new AgentInfo().serverFqdn("https://agent3:443"));

    ApiClient apiClient = mock(ApiClient.class);
    when(signalsApi.getApiClient()).thenReturn(apiClient);
    when(apiClient.getBasePath()).thenReturn("pathToTheAgent");

    ExternalLoadBalancingStrategy loadBalancingStrategy =
        new ExternalLoadBalancingStrategy(new BdkRetryConfig(), signalsApi);

    List<String> basePaths = Stream.generate(loadBalancingStrategy::getNewBasePath).limit(3)
        .collect(Collectors.toList());

    assertEquals(Arrays.asList("https://agent1:443", "https://agent2:443", "https://agent3:443"), basePaths);
  }

  @Test
  void testExternalLbStrategyWithError() throws ApiException {
    SignalsApi signalsApi = mock(SignalsApi.class);
    when(signalsApi.v1InfoGet())
        .thenThrow(new ApiException(500, "error"));

    ApiClient apiClient = mock(ApiClient.class);
    when(signalsApi.getApiClient()).thenReturn(apiClient);
    when(apiClient.getBasePath()).thenReturn("pathToTheAgent");

    ExternalLoadBalancingStrategy loadBalancingStrategy =
        new ExternalLoadBalancingStrategy(new BdkRetryConfig(1), signalsApi);

    assertThrows(ApiRuntimeException.class, loadBalancingStrategy::getNewBasePath);
  }

  @Test
  void testLoadBalancingStrategyFactoryConstructor() {
    // otherwise `gradle jacocoTestCoverageVerification` will fail on LoadBalancingStrategyFactory
    assertDoesNotThrow(LoadBalancingStrategyFactory::new);
  }

  private LoadBalancingStrategy getLoadBalancingStrategy(BdkLoadBalancingMode mode) {
    return getLoadBalancingStrategy(mode, Collections.emptyList());
  }

  private LoadBalancingStrategy getLoadBalancingStrategy(BdkLoadBalancingMode mode, List<String> hosts) {
    BdkConfig bdkConfig = getBdkConfig(mode, hosts);
    return LoadBalancingStrategyFactory.getInstance(bdkConfig, new ApiClientFactory(new BdkConfig()));
  }

  private BdkConfig getBdkConfig(BdkLoadBalancingMode mode, List<String> hosts) {
    List<BdkServerConfig> servers = hosts.stream().map(s -> {
      BdkServerConfig serverConfig = new BdkServerConfig();
      serverConfig.setHost(s);
      return serverConfig;
    }).collect(Collectors.toList());

    BdkLoadBalancingConfig loadBalancingConfig = new BdkLoadBalancingConfig();
    loadBalancingConfig.setNodes(servers);
    loadBalancingConfig.setMode(mode);

    BdkConfig config = new BdkConfig();
    config.getAgent().setLoadBalancing(loadBalancingConfig);

    return config;
  }
}
