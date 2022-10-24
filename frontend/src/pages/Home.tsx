import Header from "@components/Header";

export default function Home() {
  return (
    <div>
      <div>
        <Header />
      </div>
      <h1>Explore</h1>
      <div>
        <p>
          Edit<code>src/App.tsx</code> and save to test HMR
        </p>
        <p className="read-the-docs">
          Click on the Vite and React logos to learn more
        </p>
      </div>
      <div>
        <p>Change language:</p>
        <button type="button">fr</button>
        <button type="button">en</button>
      </div>
    </div>
  );
}
