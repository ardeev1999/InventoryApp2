package com.yourname.inventory.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class TestViewModel(application: Application) : AndroidViewModel(application) {
    // Простейший ViewModel БЕЗ Room
    private val _testText = MutableLiveData<String>("Приложение работает!")
    val testText: LiveData<String> = _testText
}