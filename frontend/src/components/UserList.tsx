import { useState, useEffect } from 'react';
import { api } from '../api/client';
import type { User } from '../types';

export function UserList() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const fetchUsers = async () => {
      try {
        const data = await api.getUsers();
        if (!cancelled) {
          setUsers(data);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (!cancelled) {
          const errObj = err as { message?: string };
          setError(errObj.message || '加载用户列表失败');
          setLoading(false);
        }
      }
    };

    fetchUsers();
    return () => { cancelled = true; };
  }, []);

  if (loading) {
    return <div className="user-list-loading">Loading...</div>;
  }

  if (error) {
    return <div className="user-list-error">Error: {error}</div>;
  }

  return (
    <div className="user-list">
      <h2>用户列表</h2>
      <ul className="user-items">
        {users.map((user) => (
          <li key={user.id} className="user-item">
            <span className="user-name">{user.name}</span>
            <span className="user-email">{user.email}</span>
          </li>
        ))}
      </ul>
      {users.length === 0 && <p className="user-empty">暂无用户数据</p>}
    </div>
  );
}
