package kezek.restaurant.core.repository

import akka.Done
import kezek.restaurant.core.domain.{Category, CategoryFilter}
import kezek.restaurant.core.util.SortType

import scala.concurrent.Future

trait CategoryRepository {

  def create(category: Category): Future[Category]

  def update(id: String, category: Category): Future[Category]

  def findById(id: String): Future[Option[Category]]

  def paginate(filters: Seq[CategoryFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[Set[Category]]

  def findAll(filters: Seq[CategoryFilter],
              sortParams: Map[String, SortType]): Future[Set[Category]]

  def count(filters: Seq[CategoryFilter]): Future[Long]

  def delete(id: String): Future[Done]
}
