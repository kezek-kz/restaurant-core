package kezek.restaurant.core.api.http.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.Product
import kezek.restaurant.core.domain.dto._
import kezek.restaurant.core.service.ProductService
import kezek.restaurant.core.util.{HttpUtil, SortUtil}

import javax.ws.rs._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait ProductHttpRoutes extends MainCodec {

  val productService: ProductService

  def productHttpRoutes: Route = {
    pathPrefix("products") {
      concat(
        uploadProductImage,
        deleteProductImage,
        updateProduct,
        getProductById,
        deleteProduct,
        paginateProducts,
        createProduct
      )
    }
  }

  @GET
  @Operation(summary = "Get product list", description = "Get filtered and paginated product list")
  @Parameter(name = "title", in = ParameterIn.QUERY)
  @Parameter(name = "description", in = ParameterIn.QUERY)
  @Parameter(name = "categorySlug", in = ParameterIn.QUERY)
  @Parameter(name = "categorySlugList", in = ParameterIn.QUERY, example = "[slug-1,slug-2]")
  @Parameter(name = "page", in = ParameterIn.QUERY, example = "1")
  @Parameter(name = "pageSize", in = ParameterIn.QUERY, example = "10")
  @Parameter(name = "sort", in = ParameterIn.QUERY, example = "+phoneNumber,-firstName")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[ProductListWithTotalDTO]))))
  @Path("/products")
  @Tag(name = "Products")
  def paginateProducts: Route = {
    get {
      pathEndOrSingleSlash {
        parameters(
          "categorySlug".?,
          "categorySlugList".as[Seq[String]],
          "title".?,
          "description".?,
          "page".as[Int].?,
          "pageSize".as[Int].?,
          "sort".?
        ) {
          (categorySlug, categorySlugList, title, description, page, pageSize, sort) => {
            onComplete {
              productService.paginate(
                ProductService.generateFilters(
                  categorySlug = categorySlug,
                  categorySlugList = categorySlugList,
                  title = title,
                  description = description
                ),
                page,
                pageSize,
                SortUtil.parseSortParams(sort)
              )
            } {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        }
      }
    }
  }

  @GET
  @Operation(summary = "Get product by id", description = "Returns a full information about product by id")
  @Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true)
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[Product]))))
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/products/{id}")
  @Tag(name = "Products")
  def getProductById: Route = {
    get {
      path(Segment) { id =>
        onComplete(productService.getById(id)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

  @POST
  @Operation(summary = "Create product", description = "Creates new product")
  @RequestBody(required = true, content = Array(new Content(schema = new Schema(implementation = classOf[CreateProductDTO]))))
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[ProductDTO]))))
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/products")
  @Tag(name = "Products")
  def createProduct: Route = {
    post {
      pathEndOrSingleSlash {
        concat(
          entity(as[CreateProductDTO]) { body =>
            onComplete(productService.create(body)) {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          },
          entity(as[Seq[CreateProductDTO]]) { body =>
            onComplete(productService.createMany(body)) {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          },
        )
      }
    }
  }

  @PUT
  @Operation(summary = "Update product", description = "Updates product")
  @Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true)
  @RequestBody(required = true, content = Array(new Content(schema = new Schema(implementation = classOf[UpdateProductDTO]))))
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[ProductDTO]))))
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/products/{id}")
  @Tag(name = "Products")
  def updateProduct: Route = {
    put {
      path(Segment) { id =>
        entity(as[UpdateProductDTO]) { body =>
          onComplete(productService.update(id, body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @DELETE
  @Operation(summary = "Deletes product", description = "Deletes product")
  @Parameter(name = "id", in = ParameterIn.PATH, required = true)
  @ApiResponse(responseCode = "204", description = "No content")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/products/{id}")
  @Tag(name = "Products")
  def deleteProduct: Route = {
    delete {
      path(Segment) { id =>
        onComplete(productService.delete(id)) {
          case Success(_) => complete(StatusCodes.NoContent)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

  @POST
  @Operation(summary = "Upload product image", description = "Uploads product image to s3 and deletes old image")
  @Parameter(name = "id", in = ParameterIn.PATH)
  @RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[UploadImageMultipartRequest]), mediaType = "multipart/form-data")))
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[ProductDTO]))))
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/products/{id}/image")
  @Tag(name = "Products")
  def uploadProductImage: Route = {
    post {
      path(Segment / "image") { id =>
        withRequestTimeout(5.minutes) {
          fileUpload("image") {
            case (fileInfo, byteSource) => {
              onComplete(productService.uploadProductImage(byteSource, id, fileInfo)) {
                case Success(result) => complete(result)
                case Failure(exception) => HttpUtil.completeThrowable(exception)
              }
            }
          }
        }
      }
    }
  }

  @DELETE
  @Operation(summary = "Delete product image", description = "Deletes product image")
  @Parameter(name = "id", in = ParameterIn.PATH)
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[ProductDTO]))))
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/products/{id}/image")
  @Tag(name = "Products")
  def deleteProductImage: Route = {
    delete {
      path(Segment / "image") { productId =>
        onComplete(productService.deleteProductImage(productId)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

}
