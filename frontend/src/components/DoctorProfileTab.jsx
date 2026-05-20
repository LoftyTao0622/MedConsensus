import { useState, useEffect, useRef } from "react";
import {
  Activity, Brain, Network, FileText, TrendingUp, Users,
  Stethoscope, Zap, Search, ChevronRight, BarChart3,
  Shield, Clock, CheckCircle2, AlertTriangle, Loader2
} from "lucide-react";
import { fetchDoctorStats, exploreGraph } from "../api/doctor";

export function DoctorProfileTab({ currentUser, sessions, patients, pipelineEvents }) {
  const [stats, setStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [graphQuery, setGraphQuery] = useState("");
  const [graphResults, setGraphResults] = useState(null);
  const [graphLoading, setGraphLoading] = useState(false);
  const [activeSection, setActiveSection] = useState("overview");

  useEffect(() => {
    loadStats();
  }, []);

  async function loadStats() {
    try {
      setStatsLoading(true);
      const data = await fetchDoctorStats();
      setStats(data);
    } catch (err) {
      console.error("Failed to load doctor stats:", err);
    } finally {
      setStatsLoading(false);
    }
  }

  async function handleGraphExplore(e) {
    e.preventDefault();
    if (!graphQuery.trim()) return;
    try {
      setGraphLoading(true);
      const data = await exploreGraph(graphQuery.trim());
      setGraphResults(data);
    } catch (err) {
      console.error("Graph explore failed:", err);
    } finally {
      setGraphLoading(false);
    }
  }

  const riskColors = {
    "低": { bg: "#dcfce7", color: "#166534" },
    "中": { bg: "#fef9c3", color: "#854d0e" },
    "中高": { bg: "#fed7aa", color: "#9a3412" },
    "高": { bg: "#fecaca", color: "#991b1b" }
  };

  const recentPipelineEvents = pipelineEvents?.slice(-5).reverse() || [];
  const totalRisk = stats?.riskDistribution ? Object.values(stats.riskDistribution).reduce((a, b) => a + b, 0) : 0;

  return (
    <div className="doctor-info-tab glass-card section full-height">
      {/* Header */}
      <div className="section-title">
        <div>
          <p className="eyebrow">Doctor Intelligence Hub</p>
          <h2>医生智能中心</h2>
        </div>
        <div className="doctor-nav-pills">
          {[
            { key: "overview", label: "总览", icon: BarChart3 },
            { key: "graph", label: "知识图谱", icon: Network },
            { key: "pipeline", label: "流水线", icon: Activity }
          ].map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              className={`doctor-nav-pill ${activeSection === key ? "active" : ""}`}
              onClick={() => setActiveSection(key)}
            >
              <Icon size={16} />
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Profile Card */}
      <div className="doctor-profile-hero">
        <div className="doctor-profile-info">
          <div className="avatar large-avatar">{currentUser?.username?.charAt(0) || "D"}</div>
          <div>
            <h2>{currentUser?.username || "医生"}</h2>
            <p className="role-badge">
              {[currentUser?.department, currentUser?.title].filter(Boolean).join(" / ")}
              {(!currentUser?.department && !currentUser?.title) ? (currentUser?.role === "ADMIN" ? "管理员" : "主治医生") : ""}
            </p>
          </div>
        </div>
        <div className="doctor-quick-stats">
          <div className="quick-stat">
            <Stethoscope size={18} />
            <div>
              <span>今日会话</span>
              <strong>{sessions?.length || 0}</strong>
            </div>
          </div>
          <div className="quick-stat">
            <Users size={18} />
            <div>
              <span>管理患者</span>
              <strong>{patients?.length || 0}</strong>
            </div>
          </div>
          <div className="quick-stat">
            <Brain size={18} />
            <div>
              <span>总诊断数</span>
              <strong>{stats?.totalDiagnoses || 0}</strong>
            </div>
          </div>
        </div>
      </div>

      {/* Overview Section */}
      {activeSection === "overview" && (
        <div className="doctor-section-content">
          {/* AI Diagnosis Profile */}
          <div className="ai-profile-section">
            <h3><Brain size={20} /> AI 诊断能力画像</h3>
            {statsLoading ? (
              <div className="loading-state">
                <Loader2 size={24} className="spin" />
                <span>加载统计数据...</span>
              </div>
            ) : stats ? (
              <div className="ai-profile-grid">
                {/* Confidence Gauge */}
                <div className="profile-metric-card">
                  <div className="metric-header">
                    <TrendingUp size={16} />
                    <span>平均置信度</span>
                  </div>
                  <div className="confidence-gauge">
                    <svg viewBox="0 0 120 120">
                      <circle cx="60" cy="60" r="50" fill="none" stroke="rgba(29, 113, 184, 0.12)" strokeWidth="10" />
                      <circle
                        cx="60" cy="60" r="50" fill="none"
                        stroke="url(#gradient)"
                        strokeWidth="10"
                        strokeLinecap="round"
                        strokeDasharray={`${stats.averageConfidence * 314} 314`}
                        transform="rotate(-90 60 60)"
                      />
                      <defs>
                        <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                          <stop offset="0%" stopColor="#1d71b8" />
                          <stop offset="100%" stopColor="#4fc1d8" />
                        </linearGradient>
                      </defs>
                    </svg>
                    <div className="gauge-value">
                      <strong>{Math.round(stats.averageConfidence * 100)}%</strong>
                      <span>置信度</span>
                    </div>
                  </div>
                </div>

                {/* AI Adoption Rate */}
                <div className="profile-metric-card">
                  <div className="metric-header">
                    <CheckCircle2 size={16} />
                    <span>AI 采纳率</span>
                  </div>
                  <div className="metric-value-large">
                    <strong>{Math.round(stats.aiAdoptionRate * 100)}%</strong>
                    <p>医生直接采纳 AI 结论的比例</p>
                  </div>
                  <div className="metric-bar">
                    <div
                      className="metric-bar-fill"
                      style={{ width: `${stats.aiAdoptionRate * 100}%` }}
                    />
                  </div>
                </div>

                {/* Diagnosis Consistency */}
                <div className="profile-metric-card">
                  <div className="metric-header">
                    <Shield size={16} />
                    <span>诊断一致性</span>
                  </div>
                  <div className="metric-value-large">
                    <strong>{Math.round(stats.diagnosisConsistency * 100)}%</strong>
                    <p>AI 与最终诊断的吻合度</p>
                  </div>
                  <div className="metric-bar">
                    <div
                      className="metric-bar-fill consistency"
                      style={{ width: `${stats.diagnosisConsistency * 100}%` }}
                    />
                  </div>
                </div>

                {/* Risk Distribution */}
                <div className="profile-metric-card risk-card">
                  <div className="metric-header">
                    <AlertTriangle size={16} />
                    <span>风险等级分布</span>
                  </div>
                  <div className="risk-distribution">
                    {Object.entries(stats.riskDistribution || {}).map(([level, count]) => (
                      <div key={level} className="risk-item">
                        <div className="risk-label">
                          <span
                            className="risk-dot"
                            style={{ background: riskColors[level]?.color || "#6b7280" }}
                          />
                          {level}风险
                        </div>
                        <div className="risk-bar-container">
                          <div
                            className="risk-bar"
                            style={{
                              width: `${totalRisk > 0 ? (count / totalRisk) * 100 : 0}%`,
                              background: riskColors[level]?.bg || "#f3f4f6"
                            }}
                          />
                        </div>
                        <span className="risk-count">{count}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div className="empty-state">暂无诊断数据</div>
            )}
          </div>

          {/* Smart Case Summary */}
          <div className="smart-summary-section">
            <h3><FileText size={20} /> 智能病例摘要</h3>
            <div className="summary-card">
              {patients?.length > 0 ? (
                <div className="summary-content">
                  <p className="summary-text">
                    您目前管理 <strong>{patients.length}</strong> 位患者，
                    其中 {patients.filter(p => p.chiefComplaint).length} 例有明确主诉记录。
                    {sessions?.length > 0 && `今日已完成 ${sessions.length} 次诊断会话。`}
                    {stats?.totalDiagnoses > 0 && `累计完成 ${stats.totalDiagnoses} 次 AI 辅助诊断。`}
                  </p>
                  <div className="summary-highlights">
                    {patients.slice(0, 3).map((p, i) => (
                      <div key={i} className="highlight-item">
                        <ChevronRight size={14} />
                        <span>
                          <strong>{p.name}</strong>
                          {p.chiefComplaint && ` - ${p.chiefComplaint.substring(0, 30)}${p.chiefComplaint.length > 30 ? '...' : ''}`}
                        </span>
                      </div>
                    ))}
                    {patients.length > 3 && (
                      <div className="highlight-more">
                        还有 {patients.length - 3} 位患者...
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <div className="empty-state">
                  <Users size={24} />
                  <p>暂无患者数据，开始添加患者以获取智能摘要</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Knowledge Graph Section */}
      {activeSection === "graph" && (
        <div className="doctor-section-content">
          <div className="graph-explore-section">
            <h3><Network size={20} /> 医学知识图谱探索</h3>
            <p className="section-desc">输入症状或疾病名称，探索知识图谱中的关联路径</p>

            <form onSubmit={handleGraphExplore} className="graph-search-form">
              <div className="graph-search-input">
                <Search size={18} />
                <input
                  type="text"
                  value={graphQuery}
                  onChange={(e) => setGraphQuery(e.target.value)}
                  placeholder="输入症状或疾病，如：发热、咳嗽、肺炎..."
                />
              </div>
              <button type="submit" className="primary-button" disabled={graphLoading}>
                {graphLoading ? <Loader2 size={18} className="spin" /> : <Search size={18} />}
                探索
              </button>
            </form>

            {graphResults && (
              <div className="graph-results">
                <div className="graph-results-header">
                  <span>查询: "{graphResults.query}"</span>
                  <span>找到 {graphResults.paths?.length || 0} 条路径</span>
                </div>

                {graphResults.paths?.length > 0 ? (
                  <div className="graph-paths">
                    {graphResults.paths.map((path, i) => (
                      <div key={i} className="graph-path-card">
                        <div className="path-header">
                          <div className="path-node symptom">
                            <Stethoscope size={14} />
                            {path.symptom}
                          </div>
                          <ChevronRight size={16} className="path-arrow" />
                          <div className="path-node disease">
                            <Brain size={14} />
                            {path.disease}
                          </div>
                          <div className="path-confidence">
                            {Math.round(path.confidence * 100)}% 置信度
                          </div>
                        </div>

                        {path.treatments?.length > 0 && (
                          <div className="path-detail">
                            <span className="detail-label">治疗方案</span>
                            <div className="detail-tags">
                              {path.treatments.map((t, j) => (
                                <span key={j} className="detail-tag treatment">{t}</span>
                              ))}
                            </div>
                          </div>
                        )}

                        {path.examinations?.length > 0 && (
                          <div className="path-detail">
                            <span className="detail-label">检查项目</span>
                            <div className="detail-tags">
                              {path.examinations.map((e, j) => (
                                <span key={j} className="detail-tag examination">{e}</span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="empty-state">
                    <Network size={24} />
                    <p>未找到相关知识图谱路径，请尝试其他关键词</p>
                  </div>
                )}
              </div>
            )}

            {!graphResults && (
              <div className="graph-placeholder">
                <Network size={48} />
                <p>输入症状或疾病开始探索知识图谱</p>
                <div className="graph-suggestions">
                  {["发热", "咳嗽", "头痛", "肺炎", "糖尿病"].map(s => (
                    <button
                      key={s}
                      className="suggestion-tag"
                      onClick={() => { setGraphQuery(s); }}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Pipeline Section */}
      {activeSection === "pipeline" && (
        <div className="doctor-section-content">
          <div className="pipeline-section">
            <h3><Activity size={20} /> 实时诊断流水线</h3>
            <p className="section-desc">查看 AI 诊断流水线的实时状态和历史事件</p>

            <div className="pipeline-status-grid">
              {[
                { name: "Collector", desc: "信息收集", icon: FileText, color: "#1d71b8" },
                { name: "Diagnosis", desc: "AI 诊断", icon: Brain, color: "#7c3aed" },
                { name: "Reviewers", desc: "多模型评审", icon: Users, color: "#059669" },
                { name: "Decision", desc: "决策层", icon: Zap, color: "#d97706" },
                { name: "Treatment", desc: "治疗建议", icon: Stethoscope, color: "#dc2626" }
              ].map(({ name, desc, icon: Icon, color }) => {
                const isActive = recentPipelineEvents.some(e => e.stage === name.toUpperCase());
                return (
                  <div key={name} className={`pipeline-stage-card ${isActive ? "active" : ""}`}>
                    <div className="stage-icon" style={{ background: `${color}18`, color }}>
                      <Icon size={20} />
                    </div>
                    <div className="stage-info">
                      <strong>{name}</strong>
                      <span>{desc}</span>
                    </div>
                    <div
                      className="stage-status-dot"
                      style={{ background: isActive ? color : "rgba(91, 117, 144, 0.2)" }}
                    />
                  </div>
                );
              })}
            </div>

            <div className="pipeline-events-log">
              <h4><Clock size={16} /> 最近事件</h4>
              {recentPipelineEvents.length > 0 ? (
                <div className="events-list">
                  {recentPipelineEvents.map((event, i) => (
                    <div key={i} className="event-item">
                      <div className="event-stage">{event.stage}</div>
                      <div className="event-message">{event.message}</div>
                      <div className="event-time">
                        {event.timestamp ? new Date(event.timestamp).toLocaleTimeString("zh-CN") : ""}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-state">
                  <Activity size={24} />
                  <p>暂无流水线事件，开始诊断以查看实时状态</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
