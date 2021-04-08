package kezek.restaurant.core.repository

import akka.Done
import kezek.restaurant.core.domain.{Product, ProductFilter}
import kezek.restaurant.core.util.SortType

import scala.concurrent.Future

trait ProductRepository {

  def create(product: Product): Future[Product]

  def update(id: String, product: Product): Future[Product]

  def findById(id: String): Future[Option[Product]]

  def paginate(filters: Seq[ProductFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[Seq[Product]]

  def count(filters: Seq[ProductFilter]): Future[Long]

  def delete(id: String): Future[Done]
}
