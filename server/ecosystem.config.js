export default {
  apps: [{
    name: 'tv-control',
    script: 'src/index.js',
    instances: 1,
    exec_mode: 'fork',
    env: {
      PORT: 3000,
      NODE_ENV: 'production'
    }
  }]
};
