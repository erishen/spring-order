import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import { OverviewPage } from './pages/OverviewPage';
import { OrderPage } from './pages/OrderPage';
import { UsersPage } from './pages/UsersPage';
import { StatusPage } from './pages/StatusPage';
import './App.css';

export function App() {
  return (
    <BrowserRouter>
      <div className="app-layout">
        <header className="app-header">
          <div className="brand">
            <span className="brand-mark">OP</span>
            <h1>Spring Order</h1>
          </div>
          <nav>
            <ul className="nav-links">
              <li>
                <NavLink to="/" end className={({ isActive }) => isActive ? 'active' : ''}>
                  总览
                </NavLink>
              </li>
              <li>
                <NavLink to="/users" className={({ isActive }) => isActive ? 'active' : ''}>
                  用户
                </NavLink>
              </li>
              <li>
                <NavLink to="/orders" className={({ isActive }) => isActive ? 'active' : ''}>
                  订单
                </NavLink>
              </li>
              <li>
                <NavLink to="/status" className={({ isActive }) => isActive ? 'active' : ''}>
                  运行态
                </NavLink>
              </li>
            </ul>
          </nav>
        </header>

        <main className="app-main">
          <Routes>
            <Route path="/" element={<OverviewPage />} />
            <Route path="/orders" element={<OrderPage />} />
            <Route path="/users" element={<UsersPage />} />
            <Route path="/status" element={<StatusPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
