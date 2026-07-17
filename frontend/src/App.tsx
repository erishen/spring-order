import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import { OrderPage } from './pages/OrderPage';
import { UsersPage } from './pages/UsersPage';
import './App.css';

export function App() {
  return (
    <BrowserRouter>
      <div className="app-layout">
        <header className="app-header">
          <h1>Order Platform</h1>
          <nav>
            <ul className="nav-links">
              <li>
                <NavLink to="/" end className={({ isActive }) => isActive ? 'active' : ''}>
                  订单
                </NavLink>
              </li>
              <li>
                <NavLink to="/users" className={({ isActive }) => isActive ? 'active' : ''}>
                  用户
                </NavLink>
              </li>
            </ul>
          </nav>
        </header>

        <main className="app-main">
          <Routes>
            <Route path="/" element={<OrderPage />} />
            <Route path="/users" element={<UsersPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
