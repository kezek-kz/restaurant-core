package kezek.restaurant.core.domain.dto

import kezek.restaurant.core.domain.Category

case class ProductDTO(id: String,
                      title: String,
                      slug: String,
                      unit: String,
                      price: BigDecimal,
                      salePrice: BigDecimal,
                      discountInPercent: Int,
                      description: String,
                      `type`: String,
                      image: Option[String],
                      categories: Seq[Category])
