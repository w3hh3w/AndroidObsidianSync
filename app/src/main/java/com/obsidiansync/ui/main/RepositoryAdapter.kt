package com.obsidiansync.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.obsidiansync.data.local.Repository
import com.obsidiansync.databinding.ItemRepositoryBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 仓库列表适配器
 */
class RepositoryAdapter(
    private val onSyncClick: (Repository) -> Unit,
    private val onDeleteClick: (Repository) -> Unit,
    private val onSettingsClick: (Repository) -> Unit
) : ListAdapter<Repository, RepositoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRepositoryBinding.inflate(
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
        private val binding: ItemRepositoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(repo: Repository) {
            binding.tvRepoName.text = repo.name
            binding.tvRepoPath.text = repo.localPath
            binding.tvProvider.text = repo.provider.uppercase()

            // 最后同步时间
            if (repo.lastSyncTime > 0) {
                val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                binding.tvLastSync.text = "最后同步: ${dateFormat.format(Date(repo.lastSyncTime))}"
            } else {
                binding.tvLastSync.text = "从未同步"
            }

            // 自动同步状态
            binding.tvAutoSync.text = if (repo.autoSync) "自动同步 (${repo.syncInterval}分钟)" else "手动同步"

            // 点击事件
            binding.btnSync.setOnClickListener { onSyncClick(repo) }
            binding.btnDelete.setOnClickListener { onDeleteClick(repo) }
            binding.btnSettings.setOnClickListener { onSettingsClick(repo) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Repository>() {
        override fun areItemsTheSame(oldItem: Repository, newItem: Repository): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Repository, newItem: Repository): Boolean {
            return oldItem == newItem
        }
    }
}
