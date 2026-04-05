package io.minelib.launcher.android;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import io.minelib.launcher.model.Instance;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@link ArrayAdapter} that backs the instance {@link android.widget.ListView}.
 * Each row shows the instance name + Minecraft version and a "Play" button.
 */
public final class InstanceAdapter extends ArrayAdapter<Instance> {

    private final Consumer<Instance> onPlay;

    public InstanceAdapter(Context context, List<Instance> instances,
                           Consumer<Instance> onPlay) {
        super(context, 0, instances);
        this.onPlay = onPlay;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_instance, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Instance inst = getItem(position);
        if (inst != null) {
            holder.tvName.setText(inst.getName());
            holder.tvVersion.setText(
                    "Minecraft " + inst.getMinecraftVersion()
                            + "  ·  " + inst.getModLoader().name());
            holder.btnPlay.setOnClickListener(v -> onPlay.accept(inst));
        }
        return convertView;
    }

    private static final class ViewHolder {
        final TextView tvName;
        final TextView tvVersion;
        final Button   btnPlay;

        ViewHolder(View root) {
            tvName    = root.findViewById(R.id.tv_instance_name);
            tvVersion = root.findViewById(R.id.tv_instance_version);
            btnPlay   = root.findViewById(R.id.btn_play);
        }
    }
}
