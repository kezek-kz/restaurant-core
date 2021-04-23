package kezek.restaurant.core.domain.dto

case class UpdateProductDTO(title: String,
                            slug: String,
                            unit: Option[String],
                            firstPrice: BigDecimal,
                            secondPrice: Option[BigDecimal],
                            description: Option[String],
                            categories: Seq[String])
