package com.parent.parental_control

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.parent.accessibility_service.AppBlockerService
import com.parent.accessibility_service.AppData
import com.parent.parental_control.databinding.ActivityMainBinding
import com.parent.parental_control.databinding.ItemBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val adapter by lazy {
        Adapter { pack, _ ->
            handleClick(pack)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)

        val apps = AppBlockerService.getAllApps(this)

        findViewById<RecyclerView>(R.id.container).adapter = adapter
        adapter.setData(apps.map { AdapterData(it, false) })

        if (!AppBlockerService.isAccessibilityServiceEnabled(this)) {
            Handler().postDelayed({
                navigateToAccessibilitySettings()
            }, 5000)
        }

        if (!AppBlockerService.checkUsageStatsPermission(this)) {
            requestPackageUsageStatsPermission()
        }

        findViewById<Button>(R.id.button).setOnClickListener { _ ->
            AppBlockerService.appsToBeBlocked(
                this,
                adapter.getData().map { it.appPackage }.toTypedArray()
            )
        }

        findViewById<Button>(R.id.buttonTime).setOnClickListener { _ ->
            val c = Calendar.getInstance()
            c.add(Calendar.SECOND, 10)
            val from = c.timeInMillis
            c.add(Calendar.MINUTE, 15)
            val to = c.timeInMillis
            AppBlockerService.workingTime(this, from, to)
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        val list = AppBlockerService.getAppUsageStatsWithData(
            this,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
        list.forEach {
            println("getAppUsageStatsWithData ${it.appData.appPackage}, ${it.usageStats.totalTimeVisible}")
        }
    }

    private fun handleClick(pack: String) {
        adapter.selected(pack)
    }

    private fun navigateToAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestPackageUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}

private class Adapter(private val listener: (String, Boolean) -> Unit) :
    RecyclerView.Adapter<Adapter.VH>() {

    private val data: MutableList<AdapterData> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(data: List<AdapterData>) {
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

    fun selected(packageName: String) {
        val selected = data.find { it.app.appPackage == packageName }!!
        val index = data.indexOf(selected)
        data.removeAt(index)
        data.add(index, selected.copy(checked = !selected.checked))
        notifyItemChanged(index)
    }

    fun getData(): List<AppData> {
        return data.filter { it.checked }.map { it.app }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position], listener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {

        private val binding = ItemBinding.bind(view)

        fun bind(data: AdapterData, listener: (String, Boolean) -> Unit) {
            binding.checkbox.text = data.app.appName
            val icon = BitmapFactory.decodeByteArray(data.app.icon, 0, data.app.icon.size)
            binding.imageView.setImageBitmap(icon)
            binding.checkbox.isChecked = data.checked
            binding.category.text = data.app.category

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                listener.invoke(data.app.appPackage, isChecked)
            }
        }
    }
}

data class AdapterData(
    val app: AppData,
    val checked: Boolean,
)