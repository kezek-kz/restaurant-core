package kezek.restaurant.core.domain

trait CategoryFilter

object CategoryFilter {

  case class ByTitleFilter(title: String) extends CategoryFilter
  case class ByMultipleSlugFilter(slugs: Set[String]) extends CategoryFilter

}


