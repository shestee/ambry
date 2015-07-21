package com.github.ambry.rest;

import com.codahale.metrics.MetricRegistry;
import com.github.ambry.clustermap.MockClusterMap;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Tests functionality of {@link RequestHandlerController}.
 */

public class RequestHandlerControllerTest {

  /**
   * Tests {@link RequestHandlerController#start()} and {@link RequestHandlerController#shutdown()}.
   * @throws InstantiationException
   * @throws IOException
   */
  @Test
  public void startShutdownTest()
      throws InstantiationException, IOException {
    RestRequestHandlerController requestHandlerController = createRestRequestHandlerController(1);
    requestHandlerController.start();
    requestHandlerController.shutdown();
  }

  /**
   * Tests that an exception is thrown when trying to instantiate a {@link RequestHandlerController} with 0 handlers.
   * @throws Exception
   */
  @Test(expected = InstantiationException.class)
  public void startWithHandlerCountZeroTest()
      throws Exception {
    createRestRequestHandlerController(0);
  }

  /**
   * Tests for {@link RequestHandlerController#shutdown()} when {@link RequestHandlerController#start()} has not been
   * called previously. This test is for cases where {@link RequestHandlerController#start()} has failed and
   * {@link RequestHandlerController#shutdown()} needs to be run.
   * @throws InstantiationException
   * @throws IOException
   */
  @Test
  public void shutdownWithoutStartTest()
      throws InstantiationException, IOException {
    RestRequestHandlerController requestHandlerController = createRestRequestHandlerController(1);
    requestHandlerController.shutdown();
  }

  /**
   * This tests for exceptions thrown when a {@link RequestHandlerController} is used without calling
   * {@link RequestHandlerController#start()} first.
   * @throws InstantiationException
   * @throws IOException
   * @throws RestServiceException
   */
  @Test
  public void useServiceWithoutStartTest()
      throws InstantiationException, IOException, RestServiceException {
    RestRequestHandlerController requestHandlerController = createRestRequestHandlerController(1);
    try {
      requestHandlerController.getRequestHandler();
      fail("RequestHandlerController did not have any running RestRequestHandlers but did not throw exception");
    } catch (RestServiceException e) {
      assertEquals("Unexpected RestServiceErrorCode", RestServiceErrorCode.NoRequestHandlersAvailable,
          e.getErrorCode());
    } finally {
      requestHandlerController.shutdown();
    }
  }

  /**
   * Tests getting of a {@link RestRequestHandler} instance.
   * @throws InstantiationException
   * @throws IOException
   * @throws RestServiceException
   */
  @Test
  public void restRequestHandlerGetTest()
      throws InstantiationException, IOException, RestServiceException {
    RestRequestHandlerController requestHandlerController = createRestRequestHandlerController(5);
    requestHandlerController.start();
    try {
      doGetRestRequestHandler(requestHandlerController, 1000);
    } finally {
      requestHandlerController.shutdown();
    }
  }

  /**
   * Tests the functionality of {@link RequestHandlerController#getRequestHandler()} when some of the
   * {@link RestRequestHandler}s that it contains are no longer running.
   * @throws InstantiationException
   * @throws IOException
   * @throws RestServiceException
   */
  @Test
  public void restRequestHandlerGetWithDeadRequestHandlersTest()
      throws InstantiationException, IOException, RestServiceException {
    RestRequestHandlerController requestHandlerController = createRestRequestHandlerController(3);
    requestHandlerController.start();
    try {
      // shutdown one RestRequestHandler.
      requestHandlerController.getRequestHandler().shutdown();
      // getRequestHandler() should not suffer.
      doGetRestRequestHandler(requestHandlerController, 1000);
      // shutdown another RestRequestHandler.
      requestHandlerController.getRequestHandler().shutdown();
      // getRequestHandler() should not suffer.
      doGetRestRequestHandler(requestHandlerController, 1000);
      // shutdown last remaining RestRequestHandler.
      requestHandlerController.getRequestHandler().shutdown();
      // getRequestHandler() should fail fast and not go into an infinite loop.
      doGetRestRequestHandler(requestHandlerController, 1);
      fail("There was no running RestRequestHandler available. The test should have thrown an exception");
    } catch (RestServiceException e) {
      assertEquals("Unexpected RestServiceErrorCode", RestServiceErrorCode.NoRequestHandlersAvailable,
          e.getErrorCode());
    } finally {
      requestHandlerController.shutdown();
    }
  }

  //helpers
  //general
  private RestRequestHandlerController createRestRequestHandlerController(int handlerCount)
      throws InstantiationException, IOException {
    RestServerMetrics restServerMetrics = new RestServerMetrics(new MetricRegistry());
    BlobStorageService blobStorageService = new MockBlobStorageService(new MockClusterMap());
    return new RequestHandlerController(handlerCount, restServerMetrics, blobStorageService);
  }

  private void doGetRestRequestHandler(RestRequestHandlerController requestHandlerController, int maxIterations)
      throws RestServiceException {
    for (int i = 0; i < maxIterations; i++) {
      RestRequestHandler requestHandler = requestHandlerController.getRequestHandler();
      assertNotNull("Obtained RestRequestHandler is null", requestHandler);
      assertTrue("Obtained RestRequestHandler is not running", requestHandler.isRunning());
    }
  }
}
