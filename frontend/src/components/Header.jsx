import {
  Bell,
  CircleUserRound,
  LogOut,
  Search,
  Settings,
  ShieldPlus
} from "lucide-react";

export function Header({ currentUser, onLogout }) {
  return (
    <header className="topbar glass-card">
      <div className="brand">
        <div className="brand-mark">
          <ShieldPlus size={22} />
        </div>
        <div>
          <p className="eyebrow">Medical Consensus Workspace</p>
          <h1>共智专医</h1>
        </div>
      </div>

      <div className="topbar-actions">
        <button className="icon-button" type="button" aria-label="搜索">
          <Search size={18} />
        </button>
        <button className="icon-button" type="button" aria-label="通知">
          <Bell size={18} />
        </button>
        <button className="icon-button" type="button" aria-label="设置">
          <Settings size={18} />
        </button>
        <div className="user-chip">
          <CircleUserRound size={18} />
          <span>{currentUser?.username || "Doctor Online"}</span>
        </div>
        <button className="icon-button" type="button" aria-label="退出登录" onClick={onLogout}>
          <LogOut size={18} />
        </button>
      </div>
    </header>
  );
}
