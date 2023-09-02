/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright © 2023 Strato Team and Contributors (https://github.com/strato-emu/)
 */

package emu.skyline.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class AvatarAdapter(private val context : Context, private val avatars : List<File>) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {
    class AvatarViewHolder(view : View) : RecyclerView.ViewHolder(view) {
        val imageView : ImageView = view.findViewById(emu.skyline.R.id.avatar_image)
    }

    override fun onCreateViewHolder(parent : ViewGroup, viewType : Int) : AvatarViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context).inflate(emu.skyline.R.layout.avatar_item, null, false)
        return AvatarViewHolder(adapterLayout)
    }

    override fun getItemCount() = avatars.size

    override fun onBindViewHolder(holder : AvatarViewHolder, position : Int) {
        val preview = BitmapFactory.decodeFile(avatars[position].absolutePath)
        holder.imageView.setImageDrawable(BitmapDrawable(context.resources, preview))
    }
}