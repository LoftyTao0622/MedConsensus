export function ReviewerResults({ reviewers = [] }) {
  if (!reviewers?.length) return <p>暂无独立评审记录。</p>;
  const valid = reviewers.filter((reviewer) => reviewer.status === "VALID" && reviewer.weight > 0).length;
  const historical = reviewers.some((reviewer) => !reviewer.status);
  return (
    <section aria-label="独立模型评审">
      <p>{historical ? "历史评审记录，未记录有效票数。" : `有效评审 ${valid} / ${reviewers.length} 票`}</p>
      <div className="record-grid">
        {reviewers.map((reviewer) => (
          <article key={reviewer.name} className="reviewer-card">
            <h3>{reviewer.name}</h3>
            <p>{reviewer.status === "ABSENT" ? "缺席 · 未计票" : `评分 ${Math.round(reviewer.score * 100)}% · 配置权重 ${Math.round(reviewer.weight * 100)}%`}</p>
            <p className="reviewer-comment">{reviewer.comment}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
