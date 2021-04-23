package kezek.restaurant.core.domain.dto

import kezek.restaurant.core.domain.Category

case class ProductDTO(id: String,
                      title: String,
                      slug: String,
                      unit: Option[String],
                      firstPrice: BigDecimal,
                      secondPrice: Option[BigDecimal],
                      description: Option[String],
                      image: Option[String],
                      categories: Set[Category])
