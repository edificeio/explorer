import loading from "@assets/screen-loading.gif";

export function LoadingScreen() {
  return (
    <>
      <div
        className="position-fixed top-0 end-0 start-0 bottom-0 d-grid justify-content-center align-items-center"
        style={{ zIndex: 2000 }}
      >
        <div
          className="bg-white rounded-circle mx-auto"
          style={{ maxWidth: "15%" }}
        >
          <img src={loading} alt="loading" />
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
}
