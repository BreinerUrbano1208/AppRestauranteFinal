package com.bremnercamilourbano.apprestaurante.model


data class Product(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    val imageUrl: String?,

    val available: Boolean = true
)

data class CartItem(
    val product: Product,
    val quantity: Int
) {
    val subtotal: Double get() = product.price * quantity
}
