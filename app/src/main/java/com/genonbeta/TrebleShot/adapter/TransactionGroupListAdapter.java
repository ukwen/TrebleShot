package com.genonbeta.TrebleShot.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.database.AccessDatabase;
import com.genonbeta.TrebleShot.object.TransferGroup;
import com.genonbeta.TrebleShot.util.FileUtils;
import com.genonbeta.TrebleShot.widget.GroupEditableListAdapter;
import com.genonbeta.android.database.CursorItem;
import com.genonbeta.android.database.SQLQuery;
import com.genonbeta.android.database.SQLiteDatabase;

import java.util.ArrayList;

/**
 * created by: Veli
 * date: 9.11.2017 23:39
 */

public class TransactionGroupListAdapter
		extends GroupEditableListAdapter<TransactionGroupListAdapter.PreloadedGroup, GroupEditableListAdapter.GroupViewHolder>
{
	private AccessDatabase mDatabase;
	private SQLQuery.Select mSelect;

	public TransactionGroupListAdapter(Context context, AccessDatabase database)
	{
		super(context, MODE_GROUP_BY_DATE);

		mDatabase = database;

		setSelect(new SQLQuery.Select(AccessDatabase.TABLE_TRANSFERGROUP));
	}

	@Override
	protected void onLoad(GroupLister<PreloadedGroup> lister)
	{
		for (PreloadedGroup group : mDatabase.castQuery(getSelect(), PreloadedGroup.class, new SQLiteDatabase.CastQueryListener<PreloadedGroup>()
		{
			@Override
			public void onObjectReconstructed(SQLiteDatabase db, CursorItem item, PreloadedGroup object)
			{
				object.assignees.addAll(db.castQuery(new SQLQuery.Select(AccessDatabase.TABLE_TRANSFERASSIGNEE).
						setWhere(AccessDatabase.FIELD_TRANSFERASSIGNEE_GROUPID + "=?", String.valueOf(object.groupId)), TransferGroup.Assignee.class));
			}
		})) {
			mDatabase.calculateTransactionSize(group.groupId, group.index);

			group.totalCount = group.index.incomingCount + group.index.outgoingCount;
			group.totalBytes = group.index.incoming + group.index.outgoing;

			group.totalFiles = getContext().getResources().getQuantityString(R.plurals.text_files, group.totalCount, group.totalCount);
			group.totalSize = FileUtils.sizeExpression(group.totalBytes, false);

			lister.offer(group);
		}
	}

	@Override
	protected PreloadedGroup onGenerateRepresentative(String representativeText)
	{
		return new PreloadedGroup(representativeText);
	}

	public SQLQuery.Select getSelect()
	{
		return mSelect;
	}

	public TransactionGroupListAdapter setSelect(SQLQuery.Select select)
	{
		if (select != null)
			mSelect = select;

		return this;
	}

	@NonNull
	@Override
	public GroupEditableListAdapter.GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
	{
		if (viewType == VIEW_TYPE_REPRESENTATIVE)
			return new GroupViewHolder(getInflater().inflate(R.layout.layout_list_title, parent, false), R.id.layout_list_title_text);

		return new GroupEditableListAdapter.GroupViewHolder(getInflater().inflate(R.layout.list_transaction_group, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull final GroupEditableListAdapter.GroupViewHolder holder, int position)
	{
		final PreloadedGroup object = getItem(position);

		if (!holder.tryBinding(object)) {
			final View parentView = holder.getView();

			ImageView image = parentView.findViewById(R.id.image);
			TextView text1 = parentView.findViewById(R.id.text);
			TextView text2 = parentView.findViewById(R.id.text2);
			TextView text3 = parentView.findViewById(R.id.text3);

			parentView.setSelected(object.isSelectableSelected());

			if ((object.index.outgoingCount == 0 && object.index.incomingCount == 0)
					|| (object.index.outgoingCount > 0 && object.index.incomingCount > 0))
				image.setImageResource(object.index.outgoingCount > 0
						? R.drawable.ic_compare_arrows_white_24dp
						: R.drawable.ic_error_white_24dp);
			else
				image.setImageResource(object.index.outgoingCount > 0
						? R.drawable.ic_file_upload_black_24dp
						: R.drawable.ic_file_download_black_24dp);

			text1.setText(object.totalFiles);
			text2.setText(object.totalSize);
			text3.setText(getContext().getResources()
					.getQuantityString(R.plurals.text_devicesShared, object.assignees.size(), object.assignees.size()));
		}
	}

	public static class PreloadedGroup
			extends TransferGroup
			implements GroupEditableListAdapter.GroupEditable
	{
		public int viewType;
		public String representativeText;

		public Index index = new Index();
		public ArrayList<Assignee> assignees = new ArrayList<>();
		public String totalFiles;
		public String totalSize;

		public int totalCount;
		public long totalBytes;

		public PreloadedGroup()
		{
		}

		public PreloadedGroup(String representativeText)
		{
			this.viewType = TransactionGroupListAdapter.VIEW_TYPE_REPRESENTATIVE;
			this.representativeText = representativeText;
		}

		@Override
		public String getComparableName()
		{
			return getSelectableFriendlyName();
		}

		@Override
		public long getComparableDate()
		{
			return dateCreated;
		}

		@Override
		public long getComparableSize()
		{
			return totalCount;
		}

		@Override
		public String getSelectableFriendlyName()
		{
			return totalFiles + " (" + totalSize + ")";
		}

		@Override
		public int getViewType()
		{
			return viewType;
		}

		@Override
		public String getRepresentativeText()
		{
			return representativeText;
		}

		@Override
		public boolean isGroupRepresentative()
		{
			return representativeText != null;
		}

		@Override
		public void setDate(long date)
		{
			this.dateCreated = date;
		}

		@Override
		public boolean setSelectableSelected(boolean selected)
		{
			return !isGroupRepresentative() && super.setSelectableSelected(selected);
		}

		@Override
		public void setSize(long size)
		{
			this.totalCount = ((Long) size).intValue();
		}

		@Override
		public void setRepresentativeText(CharSequence representativeText)
		{
			this.representativeText = String.valueOf(representativeText);
		}
	}
}
