package kezek.restaurant.core.domain

case class Product(id: String,
                   title: String,
                   slug: String,
                   unit: Option[String],
                   firstPrice: BigDecimal,
                   secondPrice: Option[BigDecimal],
                   description: Option[String],
                   image: Option[String],
                   categories: Set[String])
