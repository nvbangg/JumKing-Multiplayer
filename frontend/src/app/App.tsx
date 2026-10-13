import { SoloGame } from '../features/solo/SoloGame';

export function App() {
  if (typeof location !== 'undefined' && location.pathname === '/play') return <SoloGame />;
  return <main className="home"><h1>JumpKing</h1></main>;
}
