import React, { useState, useEffect } from 'react';

function App() {
  const [logs, setLogs] = useState([]);

  // This would fetch logs from your FastAPI server later
  const fetchLogs = async () => {
    // const response = await fetch('http://localhost:8000/view-logs');
    // setLogs(await response.json());
    setLogs([{id: 1, file: "image.sec", user: "UserA", status: "GRANTED"}]);
  };

  return (
    <div style={{ padding: '20px' }}>
      <h1>Suraksha Admin Dashboard</h1>
      <table border="1">
        <thead>
          <tr><th>File ID</th><th>User</th><th>Status</th><th>Action</th></tr>
        </thead>
        <tbody>
          {logs.map(log => (
            <tr key={log.id}>
              <td>{log.file}</td><td>{log.user}</td><td>{log.status}</td>
              <td><button>Revoke Access</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default App;