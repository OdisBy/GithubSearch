package com.odisby.githubsearch.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.odisby.githubsearch.databinding.RepositoryItemBinding
import com.odisby.githubsearch.domain.Repository

class RepositoryAdapter :
    RecyclerView.Adapter<RepositoryAdapter.ViewHolder>() {

    private val asyncListDiffer: AsyncListDiffer<Repository> = AsyncListDiffer(this, DiffCallback)

    var itemLister: (Repository) -> Unit = {}
    var btnShareLister: (Repository) -> Unit = {}
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = RepositoryItemBinding.inflate(layoutInflater, parent, false)
        return ViewHolder(binding, itemLister, btnShareLister)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(asyncListDiffer.currentList[position])

    }
    override fun getItemCount(): Int = asyncListDiffer.currentList.size
    fun updateList(repositories: List<Repository>){
        asyncListDiffer.submitList(repositories)
    }
    class ViewHolder(
        private val binding: RepositoryItemBinding,
        private val itemLister: (Repository) -> Unit,
        private val btnShareLister: (Repository) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Repository){
            binding.tvName.apply {
                text = item.name
                setOnClickListener {
                    itemLister(item)
                }
            }

            binding.ivShare.setOnClickListener {
                btnShareLister(item)
            }

        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Repository>() {

        override fun areItemsTheSame(oldItem: Repository, newItem: Repository): Boolean {
            return oldItem.htmlUrl == newItem.htmlUrl
        }

        override fun areContentsTheSame(oldItem: Repository, newItem: Repository): Boolean {
            return oldItem == newItem
        }
    }
}


