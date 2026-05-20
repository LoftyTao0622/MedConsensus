import { Activity, LayoutDashboard, Stethoscope, Users, User, LogOut, Settings } from "lucide-react";

export function NavigationSidebar({ activeTab, onTabChange, currentUser, onLogout }) {
  const tabs = [
    { id: "workspace", label: "工作台", icon: <LayoutDashboard size={20} /> },
    { id: "review", label: "评审细节", icon: <Activity size={20} /> },
    { id: "patients", label: "病例管理", icon: <Users size={20} /> },
    { id: "doctor", label: "医生信息", icon: <User size={20} /> },
    { id: "settings", label: "系统设置", icon: <Settings size={20} /> },
  ];

  return (
    <aside className="side-nav glass-card">
      <div className="side-nav-header">
        <div className="brand-mark">
          <Stethoscope size={28} />
        </div>
        <div className="brand-text">
          <span className="eyebrow">Medical Consensus</span>
          <h1>共智专医</h1>
        </div>
      </div>

      <nav className="side-nav-menu">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={`side-nav-item ${activeTab === tab.id ? "active" : ""}`}
            onClick={() => onTabChange(tab.id)}
            type="button"
          >
            {tab.icon}
            <span>{tab.label}</span>
          </button>
        ))}
      </nav>

      <div className="side-nav-footer">
        <div className="user-profile-mini">
          <div className="avatar-mini">{currentUser?.username?.charAt(0) || "D"}</div>
          <div className="user-details-mini">
            <strong>{currentUser?.username || "医生"}</strong>
            <span>{currentUser?.role === "ADMIN" ? "管理员" : "主治医生"}</span>
          </div>
        </div>
        <button className="logout-btn" onClick={onLogout} aria-label="退出登录" title="退出登录">
          <LogOut size={18} />
        </button>
      </div>
    </aside>
  );
}
