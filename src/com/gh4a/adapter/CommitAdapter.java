/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.adapter;

import org.eclipse.egit.github.core.RepositoryCommit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.utils.CommitUtils;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.StringUtils;

public class CommitAdapter extends RootAdapter<RepositoryCommit> implements View.OnClickListener {
    public CommitAdapter(Context context) {
        super(context);
    }

    @Override
    protected View createView(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_commit, parent, false);
        ViewHolder viewHolder = new ViewHolder();
        Typeface boldCondensed = Gh4Application.get(mContext).boldCondensed;

        viewHolder.tvDesc = (TextView) v.findViewById(R.id.tv_desc);
        viewHolder.tvDesc.setTypeface(boldCondensed);

        viewHolder.tvSha = (TextView) v.findViewById(R.id.tv_sha);
        viewHolder.tvSha.setTypeface(Typeface.MONOSPACE);

        viewHolder.tvExtra = (TextView) v.findViewById(R.id.tv_extra);
        viewHolder.tvComments = (TextView) v.findViewById(R.id.tv_comments);

        viewHolder.ivGravatar = (ImageView) v.findViewById(R.id.iv_gravatar);
        viewHolder.ivGravatar.setOnClickListener(this);

        v.setTag(viewHolder);
        return v;
    }

    @Override
    protected void bindView(View v, RepositoryCommit commit) {
        ViewHolder viewHolder = (ViewHolder) v.getTag();

        AvatarHandler.assignAvatar(viewHolder.ivGravatar, commit.getAuthor());
        viewHolder.ivGravatar.setTag(commit);

        String message = commit.getCommit().getMessage();
        int pos = message.indexOf('\n');
        if (pos > 0) {
            message = message.substring(0, pos);
        }

        viewHolder.tvDesc.setText(message);
        viewHolder.tvSha.setText(commit.getSha().substring(0, 10));

        int comments = commit.getCommit().getCommentCount();
        if (comments > 0) {
            viewHolder.tvComments.setText(String.valueOf(comments));
            viewHolder.tvComments.setVisibility(View.VISIBLE);
        } else {
            viewHolder.tvComments.setVisibility(View.GONE);
        }

        String extra = mContext.getString(R.string.more_commit_data,
                CommitUtils.getAuthorName(mContext, commit),
                StringUtils.formatRelativeTime(mContext, commit.getCommit().getAuthor().getDate(), false));
        viewHolder.tvExtra.setText(StringUtils.applyBoldTags(extra, null));
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.iv_gravatar) {
            RepositoryCommit commit = (RepositoryCommit) v.getTag();
            Intent intent = IntentUtils.getUserActivityIntent(mContext,
                    CommitUtils.getAuthorLogin(commit));
            if (intent != null) {
                mContext.startActivity(intent);
            }
        }
    }

    private static class ViewHolder {
        public ImageView ivGravatar;
        public TextView tvDesc;
        public TextView tvExtra;
        public TextView tvSha;
        public TextView tvComments;
    }
}