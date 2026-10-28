package ir.abrova.push.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ir.abrova.push.example.databinding.ActivityInAppBinding
import ir.abrova.push.example.databinding.ItemInAppMessageBinding
import ir.abrova.push.sdk.AbrovaPush
import ir.abrova.push.sdk.inapp.InAppButton
import ir.abrova.push.sdk.inapp.InAppMessage
import ir.abrova.push.sdk.inapp.InAppMessageCallback

/**
 * In-App Messages Activity demonstrating the In-App Messaging feature.
 *
 * Features demonstrated:
 * - Fetching available in-app messages
 * - Triggering messages by event
 * - Manual message display
 * - Message callback handling
 * - Different message types (MODAL, BANNER, FULLSCREEN, CARD)
 */
class InAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInAppBinding
    private val adapter = InAppMessageAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        setupInAppCallback()

        fetchMessages()
    }

    override fun onResume() {
        super.onResume()
        AbrovaPush.setCurrentActivity(this)
    }

    override fun onPause() {
        super.onPause()
        AbrovaPush.setCurrentActivity(null)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "In-App Messages"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        adapter.onShowMessage = { message ->
            showMessage(message)
        }
    }

    private fun setupButtons() {
        binding.btnFetchMessages.setOnClickListener {
            fetchMessages()
        }

        binding.btnTriggerAppOpen.setOnClickListener {
            AbrovaPush.triggerInAppOnAppOpen()
            Toast.makeText(this, "Triggered: on_app_open", Toast.LENGTH_SHORT).show()
        }

        binding.btnTriggerSessionStart.setOnClickListener {
            AbrovaPush.triggerInAppOnSessionStart()
            Toast.makeText(this, "Triggered: on_session_start", Toast.LENGTH_SHORT).show()
        }

        binding.btnTriggerPurchase.setOnClickListener {
            AbrovaPush.triggerInAppEvent("purchase_completed", mapOf(
                "amount" to 99.99,
                "currency" to "USD",
                "productId" to "premium_subscription"
            ))
            Toast.makeText(this, "Triggered: purchase_completed", Toast.LENGTH_SHORT).show()
        }

        binding.btnTriggerCustomEvent.setOnClickListener {
            val eventName = binding.etEventName.text.toString().trim()
            if (eventName.isBlank()) {
                Toast.makeText(this, "Enter an event name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AbrovaPush.triggerInAppEvent(eventName)
            Toast.makeText(this, "Triggered: $eventName", Toast.LENGTH_SHORT).show()
        }

        binding.btnDismiss.setOnClickListener {
            AbrovaPush.dismissInAppMessage()
            Toast.makeText(this, "Dismissed current in-app message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInAppCallback() {
        AbrovaPush.setInAppMessageCallback(object : InAppMessageCallback {
            override fun onMessageReady(message: InAppMessage) {
                runOnUiThread {
                    addLog("Ready: ${message.name}")
                }
            }

            override fun onMessageDismissed(message: InAppMessage) {
                runOnUiThread {
                    addLog("Dismissed: ${message.name}")
                }
            }

            override fun onButtonClicked(message: InAppMessage, button: InAppButton) {
                runOnUiThread {
                    addLog("Button clicked: ${button.id} in ${message.name}")
                    Toast.makeText(this@InAppActivity, "Button: ${button.text}, Action: ${button.action.value}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    addLog("Error: $error")
                }
            }
        })
    }

    private fun fetchMessages() {
        binding.progressBar.visibility = View.VISIBLE

        AbrovaPush.fetchInAppMessages { messages ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                adapter.submitList(messages)
                updateEmptyState(messages.isEmpty())
                addLog("Fetched ${messages.size} messages")
            }
        }
    }

    private fun showMessage(message: InAppMessage) {
        AbrovaPush.showInAppMessage(message.id)
        addLog("Showing: ${message.name}")
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun addLog(message: String) {
        val current = binding.tvLogs.text.toString()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        binding.tvLogs.text = "[$timestamp] $message\n$current"
    }

    override fun onDestroy() {
        super.onDestroy()
        AbrovaPush.setInAppMessageCallback(null)
    }
}

/**
 * RecyclerView Adapter for in-app messages
 */
class InAppMessageAdapter : ListAdapter<InAppMessage, InAppMessageAdapter.ViewHolder>(DiffCallback()) {

    var onShowMessage: ((InAppMessage) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInAppMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemInAppMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: InAppMessage) {
            binding.tvId.text = message.id
            binding.tvType.text = message.type.value.uppercase()
            binding.tvTitle.text = message.name
            binding.tvTrigger.text = message.triggerEvent ?: message.triggerType.value

            binding.btnShow.setOnClickListener {
                onShowMessage?.invoke(message)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<InAppMessage>() {
        override fun areItemsTheSame(oldItem: InAppMessage, newItem: InAppMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: InAppMessage, newItem: InAppMessage) =
            oldItem == newItem
    }
}
