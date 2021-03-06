/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.adapters;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.wnafee.vector.ui.MorphButton;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TorrentListAdapter extends SelectableAdapter<TorrentListAdapter.ViewHolder>
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentListAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;
    /* Filtered items */
    private List<StateItem> currentItems;
    private List<StateItem> allItems;
    private StateItem curOpenTorrent;
    private DisplayFilter displayFilter = new DisplayFilter();
    private SearchFilter searchFilter = new SearchFilter();

    public TorrentListAdapter(List<TorrentStateParcel> states, Context context,
                              int rowLayout, ViewHolder.ClickListener clickListener)
    {
        this.context = context;
        this.rowLayout = rowLayout;
        this.clickListener = clickListener;
        allItems = toStateItemObject(states);
        Collections.sort(allItems);
        currentItems = new ArrayList<>(allItems);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(rowLayout, parent, false);

        return new ViewHolder(v, clickListener, currentItems);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        TorrentStateParcel state = currentItems.get(position).getState();

        Utils.setBackground(holder.indicatorCurOpenTorrent,
                ContextCompat.getDrawable(context, android.R.color.transparent));

        if (isSelected(position)) {
            Utils.setBackground(
                    holder.itemTorrentList,
                    ContextCompat.getDrawable(context, R.drawable.default_gray_rect));
        } else {
            Utils.setBackground(
                    holder.itemTorrentList,
                    ContextCompat.getDrawable(context, R.drawable.default_rect_ripple));
        }

        holder.name.setText(state.name);

        holder.progress.setProgress(state.progress);

        String stateString = "";
        switch (state.stateCode) {
            case DOWNLOADING:
                stateString = context.getString(R.string.torrent_status_downloading);
                break;
            case SEEDING:
                stateString = context.getString(R.string.torrent_status_seeding);
                break;
            case PAUSED:
                stateString = context.getString(R.string.torrent_status_paused);
                break;
            case STOPPED:
                stateString = context.getString(R.string.torrent_status_stopped);
                break;
            case FINISHED:
                stateString = context.getString(R.string.torrent_status_finished);
                break;
            case CHECKING:
                stateString = context.getString(R.string.torrent_status_checking);
                break;
        }
        holder.state.setText(stateString);

        String counterTemplate = context.getString(R.string.download_counter_template);
        String totalBytes = Formatter.formatFileSize(context, state.totalBytes);
        String receivedBytes;
        if (state.progress == 100) {
            receivedBytes = totalBytes;
        } else {
            receivedBytes = Formatter.formatFileSize(context, state.receivedBytes);
        }

        holder.downloadCounter.setText(
                String.format(
                        counterTemplate, receivedBytes,
                        totalBytes, state.progress));

        String speedTemplate = context.getString(R.string.download_upload_speed_template);
        String downloadSpeed = Formatter.formatFileSize(context, state.downloadSpeed);
        String uploadSpeed = Formatter.formatFileSize(context, state.uploadSpeed);
        holder.downloadUploadSpeed.setText(
                String.format(speedTemplate, downloadSpeed, uploadSpeed));

        String ETA;
        if (state.ETA == -1) {
            ETA = Utils.INFINITY_SYMBOL;
        } else if (state.ETA == 0) {
            ETA = " ";
        } else {
            ETA = DateUtils.formatElapsedTime(state.ETA);
        }
        holder.ETA.setText(ETA);

        holder.pauseButton.setStartDrawable(R.drawable.pause_to_play);
        holder.pauseButton.setEndDrawable(R.drawable.play_to_pause);
        holder.pauseButton.setState(
                (state.stateCode == TorrentStateCode.PAUSED ?
                        MorphButton.MorphState.END :
                        MorphButton.MorphState.START));

        if (curOpenTorrent != null &&
                getItemPosition(curOpenTorrent) == position && Utils.isTwoPane(context)) {
            if (!isSelected(position)) {
                Utils.setBackground(
                        holder.itemTorrentList,
                        ContextCompat.getDrawable(context, R.color.accent_light));
            }

            Utils.setBackground(holder.indicatorCurOpenTorrent,
                    ContextCompat.getDrawable(context, R.color.accent));
        }
    }

    private List<StateItem> toStateItemObject(Collection<TorrentStateParcel> states)
    {
        List<StateItem> list = new ArrayList<>();
        for (TorrentStateParcel state : states) {
            list.add(new StateItem(state));
        }

        return list;
    }

    public synchronized void addItems(Collection<TorrentStateParcel> states)
    {
        List<StateItem> items = toStateItemObject(states);
        List<StateItem> statesList = displayFilter.filter(items);
        currentItems.addAll(statesList);
        Collections.sort(currentItems);

        notifyItemRangeInserted(0, statesList.size());

        allItems.addAll(items);
        Collections.sort(allItems);
    }

    /*
     * Mark the torrent as currently open.
     */

    public synchronized void markAsOpen(TorrentStateParcel state)
    {
        curOpenTorrent = new StateItem(state);
        notifyDataSetChanged();
    }

    public synchronized void updateItem(TorrentStateParcel torrentState)
    {
        StateItem item = new StateItem(torrentState);
        if (!currentItems.contains(item)) {
            StateItem state = displayFilter.filter(item);

            if (state != null) {
                currentItems.add(item);
                Collections.sort(currentItems);

                notifyItemInserted(currentItems.indexOf(state));
            }

        } else {
            int position = currentItems.indexOf(item);

            if (position >= 0) {
                currentItems.remove(position);
                StateItem state = displayFilter.filter(item);

                if (state != null) {
                    currentItems.add(position, item);
                    notifyItemChanged(position);
                } else {
                    notifyItemRemoved(position);
                }
            }
        }

        if (!allItems.contains(item)) {
            allItems.add(item);

            return;
        }

        int position = allItems.indexOf(item);

        if (position < 0) {
            return;
        }

        allItems.remove(position);
        allItems.add(position, item);
    }

    public synchronized void updateItems(Collection<TorrentStateParcel> states)
    {
        List<StateItem> items = toStateItemObject(states);

        for (StateItem item : items) {
            if (!currentItems.contains(item)) {
                StateItem state = displayFilter.filter(item);

                if (state != null) {
                    currentItems.add(state);
                    Collections.sort(currentItems);

                    notifyItemInserted(currentItems.indexOf(state));
                }

            } else {
                int position = currentItems.indexOf(item);

                if (position >= 0) {
                    currentItems.remove(position);
                    StateItem state = displayFilter.filter(item);

                    if (state != null) {
                        currentItems.add(position, item);
                        notifyItemChanged(position);
                    } else {
                        notifyItemRemoved(position);
                    }
                }
            }

            if (!allItems.contains(item)) {
                allItems.add(item);

                return;
            }

            int position = allItems.indexOf(item);

            if (position < 0) {
                return;
            }

            allItems.remove(position);
            allItems.add(position, item);
        }
    }

    public void setDisplayFilter(DisplayFilter displayFilter)
    {
        if (displayFilter == null) {
            return;
        }

        this.displayFilter = displayFilter;

        currentItems.clear();
        currentItems.addAll(displayFilter.filter(allItems));

        notifyDataSetChanged();
    }

    public void search(String searchPattern)
    {
        searchFilter.filter(searchPattern);
    }

    public void clearAll()
    {
        allItems.clear();

        int size = currentItems.size();
        if (size > 0) {
            currentItems.clear();

            notifyItemRangeRemoved(0, size);
        }
    }

    public void deleteItems(Collection<TorrentStateParcel> states)
    {
       List<StateItem> items = toStateItemObject(states);

        for (StateItem item : items) {
            currentItems.remove(item);
            allItems.remove(item);
        }

        notifyDataSetChanged();
    }

    public TorrentStateParcel getItem(int position)
    {
        if (position < 0 || position >= currentItems.size()) {
            return null;
        }

        return currentItems.get(position).getState();
    }

    public int getItemPosition(StateItem state)
    {
        return currentItems.indexOf(state);
    }

    public boolean isEmpty()
    {
        return currentItems.isEmpty();
    }

    @Override
    public int getItemCount()
    {
        return currentItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener
    {
        private Context context;
        private ClickListener listener;
        private List<StateItem> states;
        LinearLayout itemTorrentList;
        TextView name;
        MorphButton pauseButton;
        ProgressBar progress;
        TextView state;
        TextView downloadCounter;
        TextView downloadUploadSpeed;
        TextView ETA;
        View indicatorCurOpenTorrent;

        public ViewHolder(View itemView, final ClickListener listener, final List<StateItem> states)
        {
            super(itemView);

            this.context = itemView.getContext();
            this.listener = listener;
            this.states = states;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            itemTorrentList = (LinearLayout) itemView.findViewById(R.id.item_torrent_list);
            name = (TextView) itemView.findViewById(R.id.torrent_name);
            pauseButton = (MorphButton) itemView.findViewById(R.id.pause_torrent);
            pauseButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    int position = getAdapterPosition();
                    if (listener != null && position >= 0) {
                        TorrentStateParcel state = states.get(position).getState();
                        listener.onPauseButtonClicked(position, state);
                    }
                }
            });
            progress = (ProgressBar) itemView.findViewById(R.id.torrent_progress);
            Utils.colorizeProgressBar(context, progress);
            state = (TextView) itemView.findViewById(R.id.torrent_status);
            downloadCounter = (TextView) itemView.findViewById(R.id.torrent_download_counter);
            downloadUploadSpeed = (TextView) itemView.findViewById(R.id.torrent_download_upload_speed);
            ETA = (TextView) itemView.findViewById(R.id.torrent_ETA);
            indicatorCurOpenTorrent = itemView.findViewById(R.id.indicator_cur_open_torrent);
        }

        @Override
        public void onClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                TorrentStateParcel state = states.get(position).getState();
                listener.onItemClicked(position, state);
            }
        }

        @Override
        public boolean onLongClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                TorrentStateParcel state = states.get(getAdapterPosition()).getState();
                listener.onItemLongClicked(position, state);

                return true;
            }

            return false;
        }

        public interface ClickListener
        {
            void onItemClicked(int position, TorrentStateParcel torrentState);

            boolean onItemLongClicked(int position, TorrentStateParcel torrentState);

            void onPauseButtonClicked(int position, TorrentStateParcel torrentState);
        }
    }

    public static class DisplayFilter
    {
        TorrentStateCode constraintCode;

        /* Without filtering */
        public DisplayFilter()
        {

        }

        public DisplayFilter(TorrentStateCode constraint)
        {
            constraintCode = constraint;
        }

        public List<StateItem> filter(Collection<StateItem> states)
        {
            List<StateItem> filtered = new ArrayList<>();

            if (states != null) {
                if (constraintCode != null) {
                    for (StateItem state : states) {
                        if (state.getState().stateCode == constraintCode) {
                            filtered.add(state);
                        }
                    }

                } else {
                    filtered.addAll(states);
                }
            }

            return filtered;
        }

        public StateItem filter(StateItem state)
        {
            if (state == null) {
                return null;
            }

            if (constraintCode != null) {
                if (state.getState().stateCode == constraintCode) {
                    return state;
                }

            } else {
                return state;
            }

            return null;
        }
    }

    private class SearchFilter extends Filter
    {
        @Override
        protected FilterResults performFiltering(CharSequence charSequence)
        {
            currentItems.clear();

            if (charSequence.length() == 0) {
                currentItems.addAll(displayFilter.filter(allItems));
            } else {
                String filterPattern = charSequence.toString().toLowerCase().trim();

                for (StateItem state : allItems) {
                    if (state.getState().name.toLowerCase().contains(filterPattern)) {
                        currentItems.add(state);
                    }
                }
            }

            Collections.sort(currentItems);

            return new FilterResults();
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults)
        {
            notifyDataSetChanged();
        }
    }

    private class StateItem implements Comparable<StateItem>
    {
        public TorrentStateParcel state;
        public StateItem(TorrentStateParcel state)
        {
            this.state = state;
        }

        public TorrentStateParcel getState()
        {
            return state;
        }

        @Override
        public int hashCode()
        {
            return state.torrentId.hashCode();
        }

        @Override
        public int compareTo(StateItem another)
        {
            return state.name.compareTo(another.getState().name);
        }

        @Override
        public boolean equals(Object o)
        {
            return state != null && (o instanceof StateItem &&
                    (state.torrentId.equals(((StateItem) o).state.torrentId)));
        }

        @Override
        public String toString()
        {
            return state.toString();
        }
    }
}
