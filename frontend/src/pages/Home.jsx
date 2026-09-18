import Header from "../components/layout/Header";
import Hero from "../components/Hero";
import Features from "../components/Features";
import HowItWorks from "../components/HowItWorks";
import DashboardPreview from "../components/DashboardPreview";
import Footer from "../components/layout/Footer";

function Home() {
  return (
    <>
      <Header />

      <main>
        <Hero />
        <Features />
        <HowItWorks />
        <DashboardPreview />
      </main>

      <Footer />
    </>
  );
}

export default Home;