package me.saket.dank.ui.submission;

import static me.saket.dank.utils.RxUtils.applySchedulers;

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.Html;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import me.saket.dank.R;
import me.saket.dank.data.VotingManager;
import me.saket.dank.ui.subreddits.SubmissionSwipeActionsProvider;
import me.saket.dank.utils.Commons;
import me.saket.dank.utils.Dates;
import me.saket.dank.utils.JrawUtils;
import me.saket.dank.utils.RecyclerAdapterWithHeader;
import me.saket.dank.utils.RecyclerViewArrayAdapter;
import me.saket.dank.utils.Strings;
import me.saket.dank.utils.Truss;
import me.saket.dank.widgets.swipe.SwipeableLayout;
import me.saket.dank.widgets.swipe.ViewHolderWithSwipeActions;

/**
 * Shows submission details as the header of the comment list.
 */
public class SubmissionAdapterWithHeader extends RecyclerAdapterWithHeader<SubmissionAdapterWithHeader.SubmissionHeaderViewHolder, ViewHolder> {

  public static final int HEADER_COUNT = 1;

  private final VotingManager votingManager;
  private final ReplyRepository replyRepository;
  private final SubmissionSwipeActionsProvider swipeActionsProvider;
  private Submission submission;
  private SubmissionHeaderViewHolder headerViewHolder;

  public static SubmissionAdapterWithHeader wrap(RecyclerViewArrayAdapter<?, ViewHolder> commentsAdapter, View headerView,
      VotingManager votingManager, ReplyRepository replyRepository, SubmissionSwipeActionsProvider swipeActionsProvider)
  {
    if (headerView.getParent() != null) {
      ((ViewGroup) headerView.getParent()).removeView(headerView);
    }
    return new SubmissionAdapterWithHeader(commentsAdapter, headerView, votingManager, replyRepository, swipeActionsProvider);
  }

  private SubmissionAdapterWithHeader(RecyclerViewArrayAdapter<?, ViewHolder> adapterToWrap, View headerView,
      VotingManager votingManager, ReplyRepository replyRepository, SubmissionSwipeActionsProvider swipeActionsProvider)
  {
    super(adapterToWrap, headerView);
    this.votingManager = votingManager;
    this.replyRepository = replyRepository;
    this.swipeActionsProvider = swipeActionsProvider;
  }

  public void updateSubmission(Submission submission) {
    this.submission = submission;

    // RecyclerView does not support moving Views inflated manually into the list. We'll have to fix this in the future.
    if (headerViewHolder != null) {
      headerViewHolder.bind(votingManager, submission, replyRepository);
    }
  }

  @Override
  protected boolean isHeaderVisible() {
    return submission != null;
  }

  @Override
  protected SubmissionHeaderViewHolder onCreateHeaderViewHolder(View headerView) {
    //if (headerViewHolder != null) {
    //  throw new AssertionError("Header is already present!");
    //}
    headerViewHolder = new SubmissionHeaderViewHolder(headerView);
    headerViewHolder.getSwipeableLayout().setSwipeActionIconProvider(swipeActionsProvider);
    return headerViewHolder;
  }

  @Override
  protected void onBindHeaderViewHolder(SubmissionHeaderViewHolder holder, int position) {
    holder.bind(votingManager, getHeaderItem(), replyRepository);

    SwipeableLayout swipeableLayout = holder.getSwipeableLayout();
    swipeableLayout.setSwipeActions(swipeActionsProvider.getSwipeActions(submission));
    swipeableLayout.setOnPerformSwipeActionListener(action -> {
      swipeActionsProvider.performSwipeAction(action, submission, swipeableLayout);
      onBindViewHolder(holder, position);
    });
  }

  @Override
  protected Submission getHeaderItem() {
    return submission;
  }

  @Override
  protected void onHeaderViewRecycled(SubmissionHeaderViewHolder holder) {
    holder.handleOnRecycled();
  }

  public static class SubmissionHeaderViewHolder extends ViewHolder implements ViewHolderWithSwipeActions {
    @BindView(R.id.submission_title) TextView titleView;
    @BindView(R.id.submission_byline) TextView bylineView;
    private Disposable pendingSyncReplyCountDisposable = Disposables.disposed();

    public SubmissionHeaderViewHolder(View itemView) {
      super(itemView);
      ButterKnife.bind(this, itemView);
    }

    public void bind(VotingManager votingManager, Submission submission, ReplyRepository replyRepository) {
      VoteDirection pendingOrDefaultVote = votingManager.getPendingOrDefaultVote(submission, submission.getVote());
      int voteDirectionColor = Commons.voteColor(pendingOrDefaultVote);

      Truss titleBuilder = new Truss();
      titleBuilder.pushSpan(new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), voteDirectionColor)));
      titleBuilder.append(Strings.abbreviateScore(votingManager.getScoreAfterAdjustingPendingVote(submission)));
      titleBuilder.popSpan();
      titleBuilder.append("  ");
      //noinspection deprecation
      titleBuilder.append(Html.fromHtml(submission.getTitle()));
      titleView.setText(titleBuilder.build());

      pendingSyncReplyCountDisposable.dispose();
      pendingSyncReplyCountDisposable = replyRepository.streamPendingSyncReplies(ParentThread.of(submission))
          .map(pendingSyncReplies -> pendingSyncReplies.size())
          .startWith(0)
          .compose(applySchedulers())
          .subscribe(pendingSyncReplyCount -> bylineView.setText(itemView.getResources().getString(
              R.string.submission_byline,
              submission.getSubredditName(),
              submission.getAuthor(),
              Dates.createTimestamp(itemView.getResources(), JrawUtils.createdTimeUtc(submission)),
              Strings.abbreviateScore(submission.getCommentCount() + pendingSyncReplyCount)
          )));
    }

    public void handleOnRecycled() {
      pendingSyncReplyCountDisposable.dispose();
    }

    @Override
    public SwipeableLayout getSwipeableLayout() {
      return (SwipeableLayout) itemView;
    }
  }
}
