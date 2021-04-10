package kezek.restaurant.core.domain

trait ProductFilter

object ProductFilter {

  case class ByCategoryIdFilter(categoryId: String) extends ProductFilter
  case class ByTitleFilter(title: String) extends ProductFilter
  case class ByDescriptionFilter(description: String) extends ProductFilter
}
