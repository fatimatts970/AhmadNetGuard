private lateinit var binding: ActivityDashboardBinding
private val adapter = DeviceListAdapter()

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityDashboardBinding.inflate(layoutInflater)
    setContentView(binding.root)

    binding.rvDevices.layoutManager = LinearLayoutManager(this)
    binding.rvDevices.adapter = adapter

    // Button Actions
    binding.btnWifiSettings.setOnClickListener {
        startActivity(Intent(this, WifiSettingsActivity::class.java))
    }
    binding.btnLogs.setOnClickListener {
        startActivity(Intent(this, LogsActivity::class.java))
    }
    binding.btnHistory.setOnClickListener {
        startActivity(Intent(this, HistoryActivity::class.java))
    }
