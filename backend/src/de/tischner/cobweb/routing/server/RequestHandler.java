package de.tischner.cobweb.routing.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import de.tischner.cobweb.db.IRoutingDatabase;
import de.tischner.cobweb.routing.algorithms.shortestpath.IShortestPathComputation;
import de.tischner.cobweb.routing.model.graph.IEdge;
import de.tischner.cobweb.routing.model.graph.IGraph;
import de.tischner.cobweb.routing.model.graph.INode;
import de.tischner.cobweb.routing.model.graph.IPath;
import de.tischner.cobweb.routing.model.graph.road.IGetNodeById;
import de.tischner.cobweb.routing.model.graph.road.IHasId;
import de.tischner.cobweb.routing.model.graph.road.ISpatial;
import de.tischner.cobweb.routing.server.model.ERouteElementType;
import de.tischner.cobweb.routing.server.model.ETransportationMode;
import de.tischner.cobweb.routing.server.model.Journey;
import de.tischner.cobweb.routing.server.model.RouteElement;
import de.tischner.cobweb.routing.server.model.RoutingRequest;
import de.tischner.cobweb.routing.server.model.RoutingResponse;
import de.tischner.cobweb.util.RoutingUtil;
import de.tischner.cobweb.util.http.EHttpContentType;
import de.tischner.cobweb.util.http.HttpResponseBuilder;
import de.tischner.cobweb.util.http.HttpUtil;

/**
 * Class that handles a routing request. It parses the request, computes
 * corresponding shortest paths and builds and sends a proper response.<br>
 * <br>
 * To handle a request call {@link #handleRequest(RoutingRequest)}.
 *
 * @author Daniel Tischner {@literal <zabuza.dev@gmail.com>}
 * @param <N> Type of the node
 * @param <E> Type of the edge
 * @param <G> Type of the graph
 */
public final class RequestHandler<N extends INode & IHasId & ISpatial, E extends IEdge<N> & IHasId,
    G extends IGraph<N, E> & IGetNodeById<N>> {
  /**
   * Logger used for logging
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);
  /**
   * The client whose request to handle.
   */
  private final Socket mClient;
  /**
   * The algorithm to use for computing shortest path requests.
   */
  private final IShortestPathComputation<N, E> mComputation;
  /**
   * The database to use for fetching meta data for nodes and edges.
   */
  private final IRoutingDatabase mDatabase;
  /**
   * The graph used for shortest path computation.
   */
  private final G mGraph;
  /**
   * The GSON object used to format JSON responses.
   */
  private final Gson mGson;

  /**
   * Creates a new handler which handles requests of the given client using the
   * given tools.<br>
   * <br>
   * To handle a request call {@link #handleRequest(RoutingRequest)}.
   *
   * @param client      The client whose request to handle
   * @param gson        The GSON object used to format JSON responses
   * @param graph       The graph used for shortest path computation
   * @param computation The algorithm to use for computing shortest path
   *                    requests
   * @param database    The database to use for fetching meta data for nodes and
   *                    edges
   */
  public RequestHandler(final Socket client, final Gson gson, final G graph,
      final IShortestPathComputation<N, E> computation, final IRoutingDatabase database) {
    mClient = client;
    mGson = gson;
    mGraph = graph;
    mComputation = computation;
    mDatabase = database;
  }

  /**
   * Handles the given routing request. It computes shortest paths and
   * constructs and sends a proper response.
   *
   * @param request The request to handle
   * @throws IOException If an I/O exception occurred while sending a response
   */
  public void handleRequest(final RoutingRequest request) throws IOException {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Handling request: {}", request);
    }
    final long startTime = System.nanoTime();

    // Get the source and destination
    final Optional<N> sourceOptional =
        mDatabase.getInternalNodeByOsm(request.getFrom()).flatMap(id -> mGraph.getNodeById(id));
    if (!sourceOptional.isPresent()) {
      sendEmptyResponse(request, startTime);
      return;
    }
    final Optional<N> destinationOptional =
        mDatabase.getInternalNodeByOsm(request.getTo()).flatMap(id -> mGraph.getNodeById(id));
    if (!destinationOptional.isPresent()) {
      sendEmptyResponse(request, startTime);
      return;
    }

    // Nodes are known, compute the path
    final N source = sourceOptional.get();
    final N destination = destinationOptional.get();

    final Optional<IPath<N, E>> pathOptional = mComputation.computeShortestPath(source, destination);
    if (!pathOptional.isPresent()) {
      sendEmptyResponse(request, startTime);
      return;
    }

    // Path is present, build the resulting journey
    final IPath<N, E> path = pathOptional.get();
    final Journey journey = buildJourney(request, path);

    // TODO Should construction of the result be included in the time
    // measurement or not? In particular the database lookups take some time.
    final long endTime = System.nanoTime();

    // Build and send response
    final RoutingResponse response = new RoutingResponse(RoutingUtil.nanoToMilliseconds(endTime - startTime),
        request.getFrom(), request.getTo(), Collections.singletonList(journey));
    sendResponse(response);
  }

  /**
   * Builds a journey object which represents the given path.
   *
   * @param request The request the journey belongs to
   * @param path    The path the journey represents
   * @return The resulting journey
   */
  private Journey buildJourney(final RoutingRequest request, final IPath<N, E> path) {
    final long depTime = request.getDepTime();
    final long duration = (long) Math.ceil(RoutingUtil.secondsToMilliseconds(path.getTotalCost()));
    final long arrTime = depTime + duration;

    // The route needs place for at least all edges and
    // the source and destination node
    final List<RouteElement> route = new ArrayList<>(path.length() + 2);

    // Build the route
    route.add(buildNode(path.getSource()));

    // Only add path and destination if the path is not empty
    if (path.length() != 0) {
      // TODO Add nodes if transportation mode changes
      route.add(buildPath(path));
      route.add(buildNode(path.getDestination()));
    }

    return new Journey(depTime, arrTime, route);
  }

  /**
   * Builds a route element which represents the given node.
   *
   * @param node The node to represent
   * @return The resulting route element
   */
  private RouteElement buildNode(final N node) {
    final String name = mDatabase.getOsmNodeByInternal(node.getId()).flatMap(mDatabase::getNodeName).orElse("");
    final float[] coordinates = new float[] { node.getLatitude(), node.getLongitude() };
    return new RouteElement(ERouteElementType.NODE, name, Collections.singletonList(coordinates));
  }

  /**
   * Builds a route element which represents the given path.
   *
   * @param path The path to represent
   * @return The resulting route element
   */
  private RouteElement buildPath(final IPath<N, E> path) {
    // TODO The current way of constructing a name may be inappropriate
    final StringJoiner nameJoiner = new StringJoiner(", ");
    final List<float[]> geom = new ArrayList<>(path.length() + 1);

    // Add the source
    final N source = path.getSource();
    geom.add(new float[] { source.getLatitude(), source.getLongitude() });
    mDatabase.getOsmNodeByInternal(source.getId()).flatMap(mDatabase::getNodeName).ifPresent(nameJoiner::add);

    // Add all edge destinations
    int lastWayId = -1;
    for (final E edge : path) {
      final N edgeDestination = edge.getDestination();
      geom.add(new float[] { edgeDestination.getLatitude(), edgeDestination.getLongitude() });

      final int wayId = edge.getId();
      if (wayId != lastWayId) {
        mDatabase.getOsmWayByInternal(wayId).flatMap(mDatabase::getWayName).ifPresent(nameJoiner::add);
      }
      lastWayId = wayId;
    }

    return new RouteElement(ERouteElementType.PATH, ETransportationMode.CAR, nameJoiner.toString(), geom);
  }

  /**
   * Sends an empty routing response. This is usually used if no shortest path
   * could be found.
   *
   * @param request   The request to respond to
   * @param startTime The time the computation started, in nanoseconds. Must be
   *                  compatible with {@link System#nanoTime()}.
   * @throws IOException If an I/O exception occurred while sending the response
   */
  private void sendEmptyResponse(final RoutingRequest request, final long startTime) throws IOException {
    final long endTime = System.nanoTime();
    final RoutingResponse response = new RoutingResponse(RoutingUtil.nanoToMilliseconds(endTime - startTime),
        request.getFrom(), request.getTo(), Collections.emptyList());
    sendResponse(response);
  }

  /**
   * Sends the given routing response.
   *
   * @param response The response to send
   * @throws IOException If an I/O exception occurred while sending the response
   */
  private void sendResponse(final RoutingResponse response) throws IOException {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Sending response: {}", response);
    }
    final String content = mGson.toJson(response);
    HttpUtil.sendHttpResponse(
        new HttpResponseBuilder().setContentType(EHttpContentType.JSON).setContent(content).build(), mClient);
  }
}
