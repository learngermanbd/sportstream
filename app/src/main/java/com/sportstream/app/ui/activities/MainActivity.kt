package com.sportstream.app.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sportstream.app.databinding.ActivityMainBinding

/**
 * Phase 3 · Step 3.1 placeholder Main screen.
 *
 * Just inflates the title/subtitle layout. Phase 3 · Step 3.2 swaps this
 * for a real DrawerLayout + BottomNavigationView + Navigation Component
 * wiring.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
