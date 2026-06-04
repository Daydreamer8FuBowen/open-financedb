import { createApp } from 'vue'
import App from './App.vue'
import pinia from './stores'
import './style.css'

const app = createApp(App)
app.use(pinia)
app.mount('#app')
