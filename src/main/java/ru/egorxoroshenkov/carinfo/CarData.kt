package ru.egorxoroshenkov.carinfo

import com.google.firebase.database.PropertyName

data class CarData(
    @JvmField
    @PropertyName("Идентификатор устройства")
    val deviceID : String = "",
    @get:PropertyName("Скорость автомобиля")
    val speed : String,
    @get:PropertyName("Нагрузка двигателя")
    val pressure : String,
    @get:PropertyName("Обороты двигателя")
    val RPM : String,
    @get:PropertyName("Температура двигателя")
    val temp : String,
    @get:PropertyName("Расход топлива")
    val tempOil : String,
    @get:PropertyName("Расход масла")
    val usage :String,
    @get:PropertyName("Атмосферное давление")
    val compress : String,
    @get:PropertyName("Напряжение")
    val volt : String,
    @get:PropertyName("Температура на входе")
    val intakeTemp : String,
    @get:PropertyName("Положение дроссельной заслонки")
    val throttlePos : String,
    @get:PropertyName("Давление в топливной рампе")
    val railPressure : String,
    @get:PropertyName("Пройденный путь")
    val distanceTravelled : String,
    @get:PropertyName("Температура окружающего воздуха")
    val temAmbientAir : String,
    @get:PropertyName("Температура моторного масла")
    val tempEngineOil : String,
    @get:PropertyName("Широта")
    val latitude : String,
    @get:PropertyName("Долгота")
    val longitude : String,


)