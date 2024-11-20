package io.keychain.chat.views.contacts;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.keychain.chat.R;

public class ContactViewAdapter extends ArrayAdapter<ChatUser> {
    private final int layoutResource;
    private final List<ChatUser> list;

    public ContactViewAdapter(Context context, int layoutResource, List<ChatUser> userList) {
        super(context, layoutResource, userList);
        this.list = userList;
        this.layoutResource = layoutResource;
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView txtName;
        TextView txtSubName;
        TextView txtSource;
        Button btnRequest;
        Button btnAccept;
        Button btnReject;
        View rootView;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            rootView = itemView;
            txtName = itemView.findViewById(R.id.nameText);
            txtSubName = itemView.findViewById(R.id.subNameText);
            txtSource = itemView.findViewById(R.id.sourceText);
            btnRequest = itemView.findViewById(R.id.pairRequest);
            btnReject = itemView.findViewById(R.id.pairReject);
            btnAccept = itemView.findViewById(R.id.pairAccept);
        }
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        View view = convertView;
        ContactViewHolder holder = null;
        if (view == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(getContext());
            view = layoutInflater.inflate(layoutResource, null);
            holder = new ContactViewHolder(view);
            view.setTag(holder);
        } else {
            holder = (ContactViewHolder) view.getTag();
        }
        ChatUser user = list.get(position);

        if (user.name == null || user.name.isEmpty()) {
            holder.txtName.setText("-");
            holder.txtSubName.setText(user.uri);
            holder.txtName.setTypeface(null, Typeface.ITALIC);
            holder.txtSubName.setTypeface(null, Typeface.ITALIC);
        } else {
            holder.txtName.setText(user.name);
            holder.txtSubName.setText(user.subName);
            holder.txtName.setTypeface(null, Typeface.NORMAL);
            holder.txtSubName.setTypeface(null, Typeface.NORMAL);
        }
        holder.txtSource.setText(user.source);

        // Show the request button ONLY if we can pair, or just initiated a pair - not
        holder.btnRequest.setVisibility(user.onRequest != null || user.isPendingResponse ? View.VISIBLE : View.GONE);
        holder.btnRequest.setEnabled(!user.isPendingResponse);
        if (user.isPendingResponse)
            holder.btnRequest.setText("Pairing");
        else
            holder.btnRequest.setText("Pair");

        if (user.onRequest != null)
            holder.btnRequest.setOnClickListener(l -> user.onRequest.callback());

        holder.btnAccept.setVisibility(user.onAccept == null ? View.GONE : View.VISIBLE);
        if (user.onAccept != null)
            holder.btnAccept.setOnClickListener(l -> user.onAccept.callback());

        holder.btnReject.setVisibility(user.onReject == null ? View.GONE : View.VISIBLE);
        if (user.onReject != null)
            holder.btnReject.setOnClickListener(l -> user.onReject.callback());

        return view;
    }
}