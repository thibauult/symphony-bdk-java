package com.symphony.bdk.core.retry.resilience4j;

import static com.symphony.bdk.core.test.BdkRetryConfigTestHelper.ofMinimalInterval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.symphony.bdk.core.config.model.BdkRetryConfig;
import com.symphony.bdk.core.retry.RecoveryStrategy;
import com.symphony.bdk.core.retry.RetryWithRecoveryBuilder;
import com.symphony.bdk.core.retry.function.ConsumerWithThrowable;
import com.symphony.bdk.core.retry.function.SupplierWithApiException;
import com.symphony.bdk.http.api.ApiException;
import com.symphony.bdk.http.api.ApiRuntimeException;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;

import javax.ws.rs.ProcessingException;

/**
 * Test class for {@link Resilience4jRetryWithRecovery}
 */
class Resilience4jRetryWithRecoveryTest {

  //to be able to use Mockito mocks around lambdas. Otherwise, does not work, even with mockito-inline
  private static class ConcreteSupplier implements SupplierWithApiException<String> {
    @Override
    public String get() throws ApiException {
      return "";
    }
  }


  private static class ConcreteConsumer implements ConsumerWithThrowable {
    @Override
    public void consume() {
    }
  }

  @Test
  void testSupplierWithNoExceptionReturnsValue() throws Throwable {
    String value = "string";

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenReturn(value);

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name","localhost.symphony.com",
        ofMinimalInterval(), supplier, (t) -> false,
        Collections.emptyList());

    assertEquals(value, r.execute());
    verify(supplier, times(1)).get();
  }

  @Test
  void testSupplierWithExceptionShouldRetry() throws Throwable {
    String value = "string";

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get())
        .thenThrow(new ApiException(400, "error"))
        .thenReturn(value);

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier,
        (t) -> t instanceof ApiException && ((ApiException) t).isClientError(),
        Collections.emptyList());

    assertEquals(value, r.execute());
    verify(supplier, times(2)).get();
  }

  @Test
  void testSupplierWithExceptionAndNoRetryShouldFailWithException() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, "error"));

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier,
        (t) -> false, Collections.emptyList());

    assertThrows(ApiException.class, r::execute);
    verify(supplier, times(1)).get();
  }

  @Test
  void testMaxAttemptsReachedShouldFailWithException() throws ApiException {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, "error"));

    final BdkRetryConfig retryConfig = ofMinimalInterval();

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        retryConfig, supplier, (t) -> true,
        Collections.emptyList());

    assertThrows(ApiException.class, r::execute);
    verify(supplier, times(retryConfig.getMaxAttempts())).get();
  }

  @Test
  void testExceptionNotMatchingRetryPredicateShouldBeForwarded() throws ApiException {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, "error"));

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier,
        (t) -> t instanceof ApiException && ((ApiException) t).isServerError(),
        Collections.emptyList());

    assertThrows(ApiException.class, r::execute);
    verify(supplier, times(1)).get();
  }

  @Test
  void testIgnoredExceptionShouldReturnNull() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, "error"));

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier, (t) -> true,
        (e) -> true, Collections.emptyList());

    assertNull(r.execute());
    verify(supplier, times(1)).get();
  }

  @Test
  void testMatchingExceptionShouldTriggerRecoveryAndRetry() throws Throwable {
    final String value = "string";

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, "error")).thenReturn(value);

    ConcreteConsumer consumer = mock(ConcreteConsumer.class);

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier, (t) -> true,
        Collections.singletonList(new RecoveryStrategy(ApiException.class, e -> true, consumer)));

    assertEquals(value, r.execute());

    InOrder inOrder = inOrder(supplier, consumer);
    inOrder.verify(supplier).get();
    inOrder.verify(consumer).consume();
    inOrder.verify(supplier).get();
    verifyNoMoreInteractions(supplier, consumer);
  }

  @Test
  void testNonMatchingExceptionShouldNotTriggerRecoveryAndRetry() throws Throwable {
    final String value = "string";

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, "error")).thenReturn(value);

    ConcreteConsumer consumer = mock(ConcreteConsumer.class);

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier, (t) -> true,
        Collections.singletonList(new RecoveryStrategy(ApiException.class, ApiException::isServerError, consumer)));

    assertEquals(value, r.execute());
    verify(supplier, times(2)).get();
    verifyNoInteractions(consumer);
  }

  @Test
  void testThrowableInRecoveryAndNotMatchingRetryPredicateShouldBeForwarded() throws Throwable {
    final String value = "string";
    final ApiException error = new ApiException(400, "error");

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(error).thenReturn(value);

    ConcreteConsumer consumer = mock(ConcreteConsumer.class);
    doThrow(new IndexOutOfBoundsException()).when(consumer).consume();

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier,
        (t) -> t instanceof ApiException,
        Collections.singletonList(new RecoveryStrategy(ApiException.class, ApiException::isClientError, consumer)));

    assertThrows(IndexOutOfBoundsException.class, r::execute);

    InOrder inOrder = inOrder(supplier, consumer);
    inOrder.verify(supplier).get();
    inOrder.verify(consumer).consume();
    verifyNoMoreInteractions(supplier, consumer);
  }

  @Test
  void testThrowableInRecoveryAndMatchingRetryPredicateShouldLeadToRetry() throws Throwable {
    final String value = "string";
    final ApiException error = new ApiException(400, "error");

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(error).thenReturn(value);

    ConcreteConsumer consumer = mock(ConcreteConsumer.class);
    doThrow(new IndexOutOfBoundsException()).when(consumer).consume();

    Resilience4jRetryWithRecovery<String> r = new Resilience4jRetryWithRecovery<>("name", "localhost.symphony.com",
        ofMinimalInterval(), supplier,
        (t) -> true,
        Collections.singletonList(new RecoveryStrategy(ApiException.class, ApiException::isClientError, consumer)));

    assertEquals(value, r.execute());

    InOrder inOrder = inOrder(supplier, consumer);
    inOrder.verify(supplier).get();
    inOrder.verify(consumer).consume();
    inOrder.verify(supplier).get();
    verifyNoMoreInteractions(supplier, consumer);
  }

  @Test
  void testExecuteAndRetrySucceeds() throws Throwable {
    final String value = "string";

    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenReturn(value);

    assertEquals(value, Resilience4jRetryWithRecovery.executeAndRetry(new RetryWithRecoveryBuilder<String>(), "test", "serviceName", supplier));
  }

  @Test
  void testExecuteAndRetryShouldConvertApiExceptionIntoApiRuntimeException() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ApiException(400, ""));

    assertThrows(ApiRuntimeException.class,
        () -> Resilience4jRetryWithRecovery.executeAndRetry(new RetryWithRecoveryBuilder<String>(), "test", "serviceName", supplier));
  }

  @Test
  void testExecuteAndRetryShouldThrowRuntimeExceptionWhenUnknownHost() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new RuntimeException(new UnknownHostException("Unknown host")));

    try {
      Resilience4jRetryWithRecovery.executeAndRetry(
          new RetryWithRecoveryBuilder<String>().retryConfig(ofMinimalInterval(3)), "test",
          "localhost.symphony.com", supplier);
    } catch (RuntimeException e){
      assertEquals("Network error occurred while trying to connect to the \"POD\" at the following address: "
          + "localhost.symphony.com. Your host is unknown, please check that the address is correct. Also consider "
          + "checking your proxy/firewall connections.", e.getMessage());
    }
 }

  @Test
  void testExecuteAndRetryShouldThrowRuntimeExceptionWhenConnectionTimeout() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ProcessingException(new SocketTimeoutException("Connection timeout")));

    try {
      Resilience4jRetryWithRecovery.executeAndRetry(
          new RetryWithRecoveryBuilder<String>().retryConfig(ofMinimalInterval(3)), "test",
          "localhost.symphony.com", supplier);
    } catch (RuntimeException e){
      assertEquals("Timeout occurred while trying to connect to the \"POD\" at the following address: "
          + "localhost.symphony.com. Please check that the address is correct. Also consider checking your "
          + "proxy/firewall connections.", e.getMessage());
    }
  }

  @Test
  void testExecuteAndRetryShouldThrowRuntimeExceptionWhenSocketException() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ProcessingException(new SocketException("Socket exception")));

    try {
      Resilience4jRetryWithRecovery.executeAndRetry(
          new RetryWithRecoveryBuilder<String>().retryConfig(ofMinimalInterval(3)), "test",
          "localhost.symphony.com", supplier);
    } catch (RuntimeException e){
      assertEquals("An unknown error occurred while trying to connect to localhost.symphony.com. "
          + "Please check below for more information: ", e.getMessage());
    }
  }

  @Test
  void testExecuteAndRetryShouldThrowRuntimeExceptionWhenConnectionRefused() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ProcessingException(new ConnectException("Connection refused")));

    try {
      Resilience4jRetryWithRecovery.executeAndRetry(
          new RetryWithRecoveryBuilder<String>().retryConfig(ofMinimalInterval(3)), "test", "localhost.symphony.com",
          supplier);
    } catch (RuntimeException e){
      assertEquals("Connection refused while trying to connect to the \"POD\" at the following address: "
          + "localhost.symphony.com. Please check if this remote address/port is reachable. Also consider checking your "
          + "proxy/firewall connections.", e.getMessage());
    }
  }

  @Test
  void testExecuteAndRetryShouldConvertUnexpectedExceptionIntoRuntimeException() throws Throwable {
    SupplierWithApiException<String> supplier = mock(ConcreteSupplier.class);
    when(supplier.get()).thenThrow(new ArrayIndexOutOfBoundsException());

    assertThrows(RuntimeException.class,
        () -> Resilience4jRetryWithRecovery.executeAndRetry(new RetryWithRecoveryBuilder<String>(), "test", "serviceName", supplier));
  }
}
