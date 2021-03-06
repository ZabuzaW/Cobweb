package de.unifreiburg.informatik.cobweb.routing.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unifreiburg.informatik.cobweb.config.IRoutingConfigProvider;
import de.unifreiburg.informatik.cobweb.db.IRoutingDatabase;
import de.unifreiburg.informatik.cobweb.routing.algorithms.shortestpath.ShortestPathComputationFactory;
import de.unifreiburg.informatik.cobweb.routing.model.graph.ICoreNode;
import de.unifreiburg.informatik.cobweb.routing.model.graph.IGetNodeById;
import de.unifreiburg.informatik.cobweb.routing.server.model.RoutingRequest;
import de.unifreiburg.informatik.cobweb.routing.server.model.RoutingResponse;

/**
 * A server which offers a REST API that is able to answer routing requests.<br>
 * <br>
 * After construction the {@link #initialize()} method should be called.
 * Afterwards it can be started by using {@link #start()}. Request the server to
 * shutdown by using {@link #shutdown()}, the current status can be checked with
 * {@link #isRunning()}. Once a server was shutdown it should not be used
 * anymore, instead create a new one.<br>
 * <br>
 * A request may consist of departure time, source and destination nodes and
 * meta-data like desired transportation modes. A response consists of departure
 * and arrival time, together with possible routes. It also includes the time it
 * needed to answer the query and to construct the answer in milliseconds.<br>
 * <br>
 * The REST API communicates over HTTP by sending and receiving JSON objects.
 * Requests are parsed into {@link RoutingRequest} and responses into
 * {@link RoutingResponse}. Accepted HTTP methods are <code>POST</code> and
 * <code>OPTIONS</code>. The server will send <code>BAD REQUEST</code> to invalid
 * requests.<br>
 * <br>
 * The server itself handles clients in parallel using a cached thread pool. For
 * construction it wants a configuration, a graph to route on, an algorithm to
 * compute shortest paths with and a database for retrieving meta-data.
 *
 * @author Daniel Tischner {@literal <zabuza.dev@gmail.com>}
 */
public final class RoutingServer implements Runnable {
  /**
   * Logger used for logging.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RoutingServer.class);
  /**
   * The timeout used when waiting for a client to connect, in milliseconds. The
   * server status is checked after each timeout.
   */
  private static final int SOCKET_TIMEOUT = 2_000;
  /**
   * The factory to use for generating algorithms for shortest path computation.
   */
  private final ShortestPathComputationFactory mComputationFactory;
  /**
   * Configuration provider which provides the port that should be used by the
   * server.
   */
  private final IRoutingConfigProvider mConfig;
  /**
   * Database used for retrieving meta-data about graph objects like nodes and
   * edges.
   */
  private final IRoutingDatabase mDatabase;
  /**
   * The object that provides nodes by their ID.
   */
  private final IGetNodeById<ICoreNode> mNodeProvider;
  /**
   * The server socket to use for communication.
   */
  private ServerSocket mServerSocket;
  /**
   * The thread to run this server on.
   */
  private Thread mServerThread;
  /**
   * Whether or not the server thread should run.
   */
  private volatile boolean mShouldRun;

  /**
   * Creates a new routing server with the given configuration that works with
   * the given tools.<br>
   * <br>
   * After construction the {@link #initialize()} method should be called.
   * Afterwards it can be started by using {@link #start()}. Request the server
   * to shutdown by using {@link #shutdown()}, the current status can be checked
   * with {@link #isRunning()}. Once a server was shutdown it should not be used
   * anymore, instead create a new one.
   *
   * @param config             Configuration provider which provides the port
   *                           that should be used by the server
   * @param nodeProvider       The object that provides nodes by their ID
   * @param computationFactory The factory to use for generating algorithms for
   *                           shortest path computation
   * @param database           Database used for retrieving meta-data about
   *                           graph objects like nodes and edges
   */
  public RoutingServer(final IRoutingConfigProvider config, final IGetNodeById<ICoreNode> nodeProvider,
      final ShortestPathComputationFactory computationFactory, final IRoutingDatabase database) {
    mConfig = config;
    mNodeProvider = nodeProvider;
    mComputationFactory = computationFactory;
    mDatabase = database;
  }

  /**
   * Initializes the server. Call this method prior to starting the server with
   * {@link #start()}. Do not call it again afterwards.
   *
   * @throws UncheckedIOException If an I/O exception occurred while creating
   *                              the server socket.
   */
  public void initialize() throws UncheckedIOException {
    mServerThread = new Thread(this);
    try {
      mServerSocket = new ServerSocket(mConfig.getRoutingServerPort());
      mServerSocket.setSoTimeout(SOCKET_TIMEOUT);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Whether or not the server is currently running.<br>
   * <br>
   * A request to shutdown can be send using {@link #shutdown()}.
   *
   * @return <code>True</code> if the server is running, <code>false</code> otherwise
   */
  public boolean isRunning() {
    return mServerThread.isAlive();
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    final ExecutorService executor = Executors.newCachedThreadPool();
    int requestId = -1;

    LOGGER.info("Server ready and waiting for clients");
    while (mShouldRun) {
      try {
        // Accept a client, the handler will close it
        @SuppressWarnings("resource")
        final Socket client = mServerSocket.accept();

        // Handle the client
        requestId++;
        final ClientHandler handler =
            new ClientHandler(requestId, client, mNodeProvider, mComputationFactory, mDatabase);
        executor.execute(handler);
      } catch (final SocketTimeoutException e) {
        // Ignore the exception. The timeout is used to repeatedly check if the
        // server should continue running.
      } catch (final Exception e) {
        // TODO Implement some limit of repeated exceptions
        // Log every exception and try to stay alive
        LOGGER.error("Unknown exception in routing server routine", e);
      }
    }

    LOGGER.info("Routing server is shutting down");
  }

  /**
   * Requests the server to shutdown.<br>
   * <br>
   * The current status can be checked with {@link #isRunning()}. Once a server
   * was shutdown it should not be used anymore, instead create a new one.
   */
  public void shutdown() {
    mShouldRun = false;
    LOGGER.info("Set shutdown request to routing server");
  }

  /**
   * Starts the server.<br>
   * <br>
   * Make sure {@link #initialize()} is called before. Request the server to
   * shutdown by using {@link #shutdown()}, the current status can be checked
   * with {@link #isRunning()}. Once a server was shutdown it should not be used
   * anymore, instead create a new one.
   */
  public void start() {
    if (isRunning()) {
      return;
    }
    LOGGER.info("Starting routing server");
    mShouldRun = true;
    mServerThread.start();
  }

}
