package com.flowfuel.app.feature.auto.refuel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.ItemList
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.core.graphics.drawable.IconCompat

private const val ICON_SIZE_PX = 96

/**
 * Teclado numérico desenhado na própria tela do carro, como uma grade de botões
 * (dígitos 1-9, apagar, 0, confirmar). Evita o SignInTemplate/InputSignInMethod, que no
 * Android Auto abre o teclado do celular (PhoneKeyboardActivity) em vez de renderizar
 * algo no carro — e ignora KEYBOARD_NUMBER nesse desvio.
 */
fun buildKeypadItemList(
    carContext: CarContext,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    extraItem: GridItem? = null,
): ItemList {
    val builder = ItemList.Builder()
    for (d in '1'..'9') {
        builder.addItem(keypadItem(carContext, d.toString()) { onDigit(d) })
    }
    builder.addItem(keypadItem(carContext, "⌫") { onBackspace() })
    builder.addItem(keypadItem(carContext, "0") { onDigit('0') })
    builder.addItem(keypadItem(carContext, "✓") { onConfirm() })
    extraItem?.let { builder.addItem(it) }
    return builder.build()
}

/** Todo botão do teclado exige o carro parado — mesma exigência que já valia pro "Próximo" do SignInTemplate. */
fun keypadItem(carContext: CarContext, glyph: String, title: String = glyph, onClick: () -> Unit): GridItem =
    GridItem.Builder()
        .setTitle(title)
        .setImage(keypadIcon(carContext, glyph))
        .setOnClickListener(ParkedOnlyOnClickListener.create(onClick))
        .build()

/** Dígitos brutos representam décimos (ex: "1505" → 150.5), mesma convenção do odômetro no celular. */
fun rawDigitsToTenths(raw: String): Double = (raw.toLongOrNull() ?: 0L) / 10.0

/** Dígitos brutos representam centavos (ex: "28990" → 289.90), mesma convenção do preço no celular. */
fun rawDigitsToCents(raw: String): Double = (raw.toLongOrNull() ?: 0L) / 100.0

fun keypadIcon(carContext: CarContext, glyph: String): CarIcon {
    val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = ICON_SIZE_PX * 0.6f
        textAlign = Paint.Align.CENTER
    }
    val yOffset = (paint.descent() + paint.ascent()) / 2
    canvas.drawText(glyph, ICON_SIZE_PX / 2f, ICON_SIZE_PX / 2f - yOffset, paint)
    return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}
