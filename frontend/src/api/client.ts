import axios, {type AxiosInstance, type InternalAxiosRequestConfig} from 'axios'

function readToken(): string | null {
    if (typeof localStorage === 'undefined') return null
    return localStorage.getItem('auth')
}

const client: AxiosInstance = axios.create({
    baseURL: '/',
})

client.interceptors.request.use((cfg: InternalAxiosRequestConfig) => {
    const token = readToken()
    if (token) {
        cfg.headers = cfg.headers ?? {}
        cfg.headers['Authorization'] = `Bearer ${token}`
    }
    return cfg
})

export default client
