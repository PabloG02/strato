/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright © 2023 Strato Team and Contributors (https://github.com/strato-emu/)
 */

package emu.skyline.fragments

import android.R
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import emu.skyline.adapter.AvatarAdapter
import emu.skyline.adapter.GridSpacingItemDecoration
import java.io.File


class AvatarPickerFragment(private val avatars: List<File>): DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(emu.skyline.R.layout.avatar_picker_fragment, container, false)

        val mRecyclerView = view.findViewById(emu.skyline.R.id.avatar_recycler_view) as RecyclerView
        val adapter = AvatarAdapter(requireContext(), avatars)
        mRecyclerView.layoutManager = StaggeredGridLayoutManager(3, LinearLayoutManager.VERTICAL)
        //mRecyclerView.addItemDecoration(GridSpacingItemDecoration(resources.getDimensionPixelSize(emu.skyline.R.dimen.grid_padding)))
        mRecyclerView.adapter = adapter

        return view
    }

    /*override fun onCreateDialog(savedInstanceState : Bundle?) : Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }*/
}