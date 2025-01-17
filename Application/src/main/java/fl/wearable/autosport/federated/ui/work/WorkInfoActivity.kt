package fl.wearable.autosport.federated.ui.work

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.android.synthetic.main.activity_main.chart
import kotlinx.android.synthetic.main.activity_main.progressBar
import kotlinx.android.synthetic.main.activity_main.toolbar
import fl.wearable.autosport.R
import fl.wearable.autosport.databinding.ActivityWorkInfoBinding
import fl.wearable.autosport.federated.service.WorkerRepository
import fl.wearable.autosport.federated.ui.ContentState
import fl.wearable.autosport.federated.ui.ProcessData

private const val TAG = "WorkInfoActivity"

@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
class WorkInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWorkInfoBinding
    private lateinit var viewModel: WorkInfoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_work_info)
        binding.lifecycleOwner = this
        setSupportActionBar(toolbar)
        viewModel = initiateViewModel()

        binding.viewModel = viewModel

        viewModel.getRunningWorkInfo()?.observe(this, viewModel.getWorkInfoObserver())

        viewModel.processState.observe(
            this,
            Observer { onProcessStateChanged(it) }
        )

        viewModel.processData.observe(
            this,
            Observer { onProcessData(it) }
        )

        viewModel.steps.observe(
            this,
            Observer { binding.step.text = it })
    }

    private fun onProcessData(it: ProcessData?) {
        processData(
            it ?: ProcessData(
                emptyList()
            )
        )
    }

    private fun onProcessStateChanged(contentState: ContentState?) {
        when (contentState) {
            ContentState.Training -> {
                progressBar.visibility = ProgressBar.GONE
                binding.chartHolder.visibility = View.VISIBLE
            }
            ContentState.Loading -> {
                progressBar.visibility = ProgressBar.VISIBLE
                binding.chartHolder.visibility = View.GONE
            }
        }
    }

    private fun processData(processState: ProcessData) {
        val entries = mutableListOf<Entry>()
        processState.data.forEachIndexed { index, value ->
            entries.add(Entry(index.toFloat(), value))
        }
        val dataSet = LineDataSet(entries, "loss")
        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.setMaxVisibleValueCount(0)
        chart.setNoDataText("Waiting for data")
        chart.invalidate()
    }

    private fun initiateViewModel(): WorkInfoViewModel {
        return ViewModelProvider(
            this,
            WorkInfoViewModelFactory(
                WorkerRepository(this)
            )
        ).get(WorkInfoViewModel::class.java)
    }
}