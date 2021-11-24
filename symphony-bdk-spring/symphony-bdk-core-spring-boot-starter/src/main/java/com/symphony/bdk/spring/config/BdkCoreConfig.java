package com.symphony.bdk.spring.config;

import com.symphony.bdk.core.auth.AuthSession;
import com.symphony.bdk.core.auth.AuthenticatorFactory;
import com.symphony.bdk.core.auth.ExtensionAppTokensRepository;
import com.symphony.bdk.core.auth.exception.AuthInitializationException;
import com.symphony.bdk.core.auth.exception.AuthUnauthorizedException;
import com.symphony.bdk.core.auth.impl.InMemoryTokensRepository;
import com.symphony.bdk.core.client.ApiClientFactory;
import com.symphony.bdk.gen.api.model.ExtensionAppTokens;
import com.symphony.bdk.http.api.ApiClient;
import com.symphony.bdk.http.jersey2.ApiClientBuilderProviderJersey2;
import com.symphony.bdk.spring.SymphonyBdkCoreProperties;

import com.symphony.bdk.template.api.TemplateEngine;
import com.symphony.bdk.template.freemarker.FreeMarkerEngine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Configuration and injection of the main BDK/Core classes as beans within the Spring application context.
 */
@Slf4j
public class BdkCoreConfig {

  @Bean
  @ConditionalOnMissingBean
  public ApiClientFactory apiClientFactory(SymphonyBdkCoreProperties properties) {
    return new ApiClientFactory(properties, new ApiClientBuilderProviderJersey2()); // TODO create RestTemplate/or WebClient implementation
  }

  @Bean(name = "agentApiClient")
  public ApiClient agentApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getAgentClient();
  }

  @Bean(name = "datafeedAgentApiClient")
  public ApiClient datafeedAgentApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getDatafeedAgentClient();
  }

  @Bean(name = "podApiClient")
  public ApiClient podApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getPodClient();
  }

  @Bean(name = "relayApiClient")
  public ApiClient relayApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getRelayClient();
  }

  @Bean(name = "loginApiClient")
  public ApiClient loginApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getLoginClient();
  }

  @Bean(name = "keyAuthApiClient")
  @ConditionalOnProperty("bdk.bot.certificatePath")
  public ApiClient keyAuthApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getKeyAuthClient();
  }

  @Bean(name = "sessionAuthApiClient")
  @ConditionalOnProperty("bdk.bot.certificatePath")
  public ApiClient sessionAuthApiClient(ApiClientFactory apiClientFactory) {
    return apiClientFactory.getSessionAuthClient();
  }

  @Bean
  @ConditionalOnMissingBean
  public ExtensionAppTokensRepository extensionAppTokensRepository() {
    return new InMemoryTokensRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthenticatorFactory authenticatorFactory(SymphonyBdkCoreProperties properties, ApiClientFactory apiClientFactory, ExtensionAppTokensRepository extensionAppTokensRepository) {
    return new AuthenticatorFactory(properties, apiClientFactory, extensionAppTokensRepository);
  }

  @Bean
  @ConditionalOnMissingBean
  public TemplateEngine templateEngine() {
    return new FreeMarkerEngine();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("bdk.bot.username")
  public AuthSession botSession(AuthenticatorFactory authenticatorFactory) {
    try {
      return authenticatorFactory.getBotAuthenticator().authenticateBot();
    } catch (AuthUnauthorizedException | AuthInitializationException e) {
      throw new BeanInitializationException("Unable to authenticate bot", e);
    }
  }
}
