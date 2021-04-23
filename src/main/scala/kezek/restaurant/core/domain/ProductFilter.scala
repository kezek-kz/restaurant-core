package kezek.restaurant.core.domain

trait ProductFilter

object ProductFilter {

  case class ByCategorySlugFilter(categorySlug: String) extends ProductFilter
  case class ByCategorySlugListFilter(categorySlugList: Seq[String]) extends ProductFilter
  case class ByTitleFilter(title: String) extends ProductFilter
  case class ByDescriptionFilter(description: String) extends ProductFilter
}
