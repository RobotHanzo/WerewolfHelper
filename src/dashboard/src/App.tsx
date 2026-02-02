import {Route, Routes} from 'react-router-dom';
import {AuthCallback} from '@/features/auth/components/AuthCallback';
import {AccessDenied} from '@/features/auth/components/AccessDenied';
import {LoginPage} from '@/features/auth/pages/LoginPage';
import {ServerSelectionPage} from '@/features/server-selection/pages/ServerSelectionPage';
import {Dashboard} from '@/features/game/components/Dashboard';

const App = () => {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage/>}/>
            <Route path="/auth/callback" element={<AuthCallback/>}/>
            <Route path="/access-denied" element={<AccessDenied/>}/>
            <Route path="/" element={<ServerSelectionPage/>}/>
            <Route path="/server/:guildId/*" element={<Dashboard/>}/>
        </Routes>
    );
};

export default App;