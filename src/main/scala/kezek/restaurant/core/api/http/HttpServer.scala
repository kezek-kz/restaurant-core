package kezek.restaurant.core.api.http

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.{Config, ConfigFactory}
import kezek.restaurant.core.service.{CategoryService, ProductService}
import kezek.restaurant.core.swagger.{SwaggerDocService, SwaggerSite}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

case class HttpServer()(implicit val actorSystem: ActorSystem[_],
                        implicit val executionContext: ExecutionContext,
                        implicit val categoryService: CategoryService,
                        implicit val productService: ProductService)
  extends HttpRoutes with SwaggerSite {

  implicit val config: Config = ConfigFactory.load()

  private val shutdown = CoordinatedShutdown(actorSystem)

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def run(): Unit =
    Http()
      .newServerAt(
        interface = config.getString("http-server.interface"),
        port = config.getInt("http-server.port")
      )
      .bind(
        concat (routes, swaggerSiteRoute, new SwaggerDocService().routes)
      )
      .onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          actorSystem.log.info("restaurant-core online at http://{}:{}/", address.getHostString, address.getPort)

          shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
            binding.terminate(10.seconds).map { _ =>
              actorSystem.log
                .info("restaurant-core http://{}:{}/ graceful shutdown completed", address.getHostString, address.getPort)
              Done
            }
          }
        case Failure(ex) =>
          actorSystem.log.error("Failed to bind HTTP endpoint, terminating system", ex)
          actorSystem.terminate()
      }

}
