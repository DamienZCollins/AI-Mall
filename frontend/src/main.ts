import { createApp } from 'vue';
import { createPinia } from 'pinia';

import App from './App.vue';
import { setupUnauthorizedHandler } from './features/auth';
import router from './router';
import { useAuthStore } from './stores/auth';
import './styles/index.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);

const auth = useAuthStore(pinia);
auth.restore();

setupUnauthorizedHandler(router, auth);

app.mount('#app');
