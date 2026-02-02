import {LoginScreen} from '../components/LoginScreen';

export const LoginPage = () => {
    const handleLogin = () => {
        window.location.href = '/api/auth/login';
    };
    return <LoginScreen onLogin={handleLogin}/>;
};
