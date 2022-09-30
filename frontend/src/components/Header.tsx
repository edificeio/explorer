import reactLogo from "../assets/images/react.svg";

export default function Header() {
  return (
    <div>
      <a href="https://vitejs.dev" target="_blank" rel="noreferrer noopener">
        <img src="/vite.svg" className="logo" alt="Vite logo" />
      </a>
      <a href="https://reactjs.org" target="_blank" rel="noreferrer noopener">
        <img src={reactLogo} className="logo react" alt="React logo" />
      </a>
    </div>
  );
}
